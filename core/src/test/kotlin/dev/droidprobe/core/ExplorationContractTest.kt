package dev.droidprobe.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** JVM protocol fixture, not evidence of Android UI Automator execution. */
private class ProtocolFixtureDriver : Driver {
    private val engine = SampleEngine()
    private var seq = 0L
    private var orientation = Orientation.PORTRAIT
    override suspend fun reset(runId: String, mode: AppMode, fixtures: Fixtures, faults: FaultConfig, orientation: Orientation) {
        engine.reset(runId, mode, fixtures, faults); this.orientation = orientation
    }
    override suspend fun observe(history: List<String>): Observation {
        val s = engine.state
        fun button(key: String, label: String, enabled: Boolean = true) = UiElement(key, label, enabled)
        val elements = when (s.screen) {
            "Home" -> listOf(button("home_product", "View product"), button("home_cart", "Cart"), button("home_draft", "Draft editor"), button("home_orders", "Order history"))
            "Product" -> listOf(button("product_add", "Add product to cart"))
            "Cart" -> listOf(button("cart_checkout", "Checkout", s.cartItems > 0), button("cart_product", "View product"))
            "Checkout" -> listOf(button("checkout_submit", "Submit checkout", s.phase in setOf("idle", "error")), button("checkout_orders", "Order history"))
            "Draft" -> listOf(UiElement("draft_content", "Draft content", true, true, s.draftInput), button("draft_save", "Save draft", s.draftInput.isNotEmpty()))
            else -> emptyList()
        } + if (s.screen == "Home") emptyList() else listOf(button("navigate_home", "Home"))
        return Observation(s.runId, ++seq, SAMPLE_PACKAGE, screen = s.screen, orientation = orientation, foreground = s.foreground,
            elements = if (s.foreground) elements else emptyList(), recentActions = history, sdk = s, elapsedMs = seq,
            synchronizedThroughEvent = s.events.lastOrNull()?.sequence ?: 0)
    }
    override suspend fun execute(action: ActionIR) {
        when (action.type) {
            ActionType.LAUNCH -> { engine.created(false); engine.foreground(true) }
            ActionType.REOPEN -> engine.foreground(true)
            ActionType.BACK -> engine.navigate("Home")
            ActionType.BACKGROUND -> engine.foreground(false)
            ActionType.CONFIGURE_FAULT -> engine.configure(action.fault!!)
            ActionType.RELEASE_FAULT -> engine.release()
            ActionType.ROTATE -> { if (orientation != action.orientation) { orientation = action.orientation!!; engine.created(true) } }
            ActionType.ENTER_TEXT -> engine.editDraft(action.text!!)
            ActionType.WAIT -> if (!action.condition!!.matches(engine.state, engine.state.foreground)) throw WorkflowTimeout("fixture event missing")
            ActionType.EVALUATE -> Unit
            ActionType.TAP -> when (action.selector!!.key) {
                "home_cart" -> engine.navigate("Cart")
                "home_product", "cart_product" -> engine.navigate("Product")
                "product_add" -> engine.addToCart()
                "cart_checkout" -> engine.navigate("Checkout")
                "checkout_submit" -> engine.submit()
                "home_draft" -> engine.navigate("Draft")
                "draft_save" -> engine.saveDraft()
                "home_orders", "checkout_orders" -> engine.navigate("Orders")
                "navigate_home" -> engine.navigate("Home")
                else -> throw PreconditionFailure("unknown semantic key")
            }
        }
    }
}
class ExplorationContractTest {
    @Test fun graphDiscoveryRecordsAReplayableSequenceWithoutFixturePlan() = runBlocking {
        val driver = ProtocolFixtureDriver(); val config = RunConfig(actionBudget = 40)
        val report = Explorer(driver).run("jvm-protocol-fixture", config, GraphPlanner())
        assertEquals(report.status, 1, report.findings.size)
        assertEquals(Oracles.UNIQUE_ORDER, report.findings.first().assertion.id)
        assertTrue(report.totalExecutedActions <= config.actionBudget)
        val scenario = Scenario(name = "observed", faults = config.faults, actions = report.records.map { it.action }, assertion = AssertionSpec(report.findings.first().assertion.id))
        val replay = ReplayEngine(driver)
        assertEquals(ReplayStatus.REPRODUCED, replay.replay(scenario, "jvm-repeat").status)
        assertEquals(ReplayStatus.FAILURE_NOT_OBSERVED, replay.replay(scenario, "jvm-fixed", AppMode.CORRECTED).status)
        assertEquals(report.records.size - 1, report.graph.transitions.size)
    }
    @Test fun absentModelUsesBaselineAndHonorsSmallActionBudget() = runBlocking {
        val planner = LlmPlanner(null)
        val report = Explorer(ProtocolFixtureDriver()).run("jvm-absent-model", RunConfig(actionBudget = 3, planner = "local"), planner)
        assertEquals(3, report.totalExecutedActions); assertTrue(report.findings.isEmpty())
        assertTrue(report.plannerIdentity.startsWith("graph-baseline")); assertEquals(0, report.plannerStats.inferenceCount)
        assertTrue(report.plannerStats.fallbacks > 0)
    }
    @Test fun randomBaselineIsDeterministicAtTheSameSeed() = runBlocking {
        suspend fun run() = Explorer(ProtocolFixtureDriver()).run("jvm-random", RunConfig(seed = 11, actionBudget = 12), RandomPlanner(11))
        val a = run(); val b = run()
        assertEquals(a.records.map { it.action.key() }, b.records.map { it.action.key() })
        assertTrue(a.totalExecutedActions <= 12); assertEquals("random-baseline", a.plannerIdentity)
    }
}
