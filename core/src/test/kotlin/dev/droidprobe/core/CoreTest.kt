package dev.droidprobe.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private fun observation(s: AppState = AppState(), elements: List<UiElement> = listOf(UiElement("submit", "Submit", true))) =
        Observation("test", 1, SAMPLE_PACKAGE, screen = s.screen, orientation = Orientation.PORTRAIT, foreground = true, elements = elements, sdk = s, elapsedMs = 1)
    private fun checkout(mode: AppMode): SampleEngine {
        val e = SampleEngine(); e.reset("run", mode, Fixtures(initialCartItems = 1), FaultConfig(holdAcknowledgement = true)); e.navigate("Checkout"); e.submit(); return e
    }
    @Test fun identicalUiRetainsPersistedPendingBusinessDistinction() {
        val idle = observation(AppState(phase = "idle"))
        val pending = observation(checkout(AppMode.FAULTY).state)
        assertNotEquals(StateNormalizer.signature(idle), StateNormalizer.signature(pending))
        assertEquals(StateNormalizer.signature(pending), StateNormalizer.signature(pending.copy(runId = "other", elapsedMs = 999, sequence = 999)))
        assertNotEquals(StateNormalizer.signature(pending), StateNormalizer.signature(pending.copy(sdk = pending.sdk!!.copy(phase = "completed"))))
    }
    @Test fun faultyRotationIsAnExecutableBusinessFailure() {
        val e = checkout(AppMode.FAULTY); e.created(true); e.release()
        val r = Oracles.evaluate(AssertionSpec(Oracles.UNIQUE_ORDER), e.state)
        assertTrue(r.applicable); assertFalse(r.passed); assertEquals("orderCount = 2", r.observed)
        assertEquals(1, e.state.events.count { it.type == "lifecycleResubmission" })
    }
    @Test fun correctedRotationRetainsOperationAndUniqueRecord() {
        val e = checkout(AppMode.CORRECTED); val op = e.state.operationId; e.created(true); e.created(true); e.release()
        assertEquals(op, e.state.operationId); assertEquals(1, e.state.orders.size)
        assertTrue(Oracles.evaluate(AssertionSpec(Oracles.UNIQUE_ORDER), e.state).passed)
    }
    @Test fun correctedBackendIsIdempotentEvenIfTheSameRequestIsRetried() {
        val e = checkout(AppMode.CORRECTED); e.retryPending(); e.retryPending()
        assertEquals(1, e.state.orders.size); assertEquals(2, e.state.events.count { it.type == "idempotencyHit" })
        assertFalse(Oracles.evaluate(AssertionSpec(Oracles.UNIQUE_ORDER, "nonexistent"), e.state).applicable)
    }
    @Test fun acknowledgedDraftPredicateIsTheSameForBothModes() {
        AppMode.entries.forEach { mode ->
            val e = SampleEngine(); e.reset("draft", mode, Fixtures(), FaultConfig()); e.editDraft("saved content"); e.saveDraft(); e.created(true)
            val r = Oracles.evaluate(AssertionSpec(Oracles.DRAFT), e.state)
            assertTrue(r.applicable); assertEquals(mode == AppMode.CORRECTED, r.passed); assertEquals("saved content", r.expected)
        }
    }
    @Test fun fixtureResetRemovesCrossRunContamination() {
        val e = checkout(AppMode.FAULTY); e.created(true); e.editDraft("old"); e.saveDraft()
        e.reset("next", AppMode.CORRECTED, Fixtures(), FaultConfig())
        assertTrue(e.state.orders.isEmpty()); assertNull(e.state.operationId); assertNull(e.state.acknowledgedDraft)
        assertEquals(0, e.state.operationCounter); assertEquals(0, e.state.generation); assertEquals(1, e.state.events.size)
        assertTrue(e.state.events.all { it.runId == "next" }); assertEquals(FaultConfig(), e.state.faults)
    }
    @Test fun controlledErrorNeverConfirmsSuccess() {
        val e = SampleEngine(); e.reset("error", AppMode.FAULTY, Fixtures(initialCartItems = 1), FaultConfig(requestError = true)); e.navigate("Checkout"); e.submit(); e.release()
        assertTrue(e.state.orders.isEmpty()); assertEquals("error", e.state.phase)
        val r = Oracles.evaluate(AssertionSpec(Oracles.ERROR), e.state); assertTrue(r.applicable && r.passed)
    }
    @Test fun validatorRejectsForeignAbsentDisabledSelectorsAndUnsupportedVersion() {
        val c = ValidationContext(observation(), 10)
        val good = ActionIR("a", ActionType.TAP, selector = Selector("submit"))
        assertTrue(ActionValidator.validate(good, c).valid)
        listOf(good.copy(selector = Selector("missing")), good.copy(selector = Selector("submit", "other.package")),
            good.copy(schemaVersion = 99), good.copy(packageName = "other.package"), good.copy(text = "illegal"), good.copy(dependsOn = listOf("unexecuted")))
            .forEach { assertFalse(ActionValidator.validate(it, c).valid) }
        assertFalse(ActionValidator.validate(good, c.copy(observation = observation(elements = listOf(UiElement("submit", "Submit", false))))).valid)
        assertFalse(ActionValidator.validate(good, c.copy(remainingActions = 0)).valid)
        assertFalse(ActionValidator.validate(good, c.copy(repetitions = mapOf(good.key() to 4))).valid)
    }
    @Test fun schemaRejectsShellAndUnknownFields() {
        assertThrows(Exception::class.java) { ProbeJson.decodeFromString<ActionIR>("""{"id":"a","type":"SHELL","command":"rm"}""") }
        assertThrows(Exception::class.java) { ProbeJson.decodeFromString<ActionIR>("""{"id":"a","type":"BACK","command":"anything"}""") }
    }
    @Test fun validatorRequiresTypedBoundsAndApplicablePreconditions() {
        val c = ValidationContext(observation(), 10)
        val text = ActionIR("input", ActionType.ENTER_TEXT, selector = Selector("submit"), text = "x")
        assertFalse(ActionValidator.validate(text, c).valid)
        assertFalse(ActionValidator.validate(ActionIR("wait", ActionType.WAIT), c).valid)
        assertFalse(ActionValidator.validate(ActionIR("rotate", ActionType.ROTATE, orientation = Orientation.LANDSCAPE,
            preconditions = listOf(Condition(ConditionType.ORDER_PERSISTED))), c).valid)
        assertFalse(ActionValidator.validate(ActionIR("assert", ActionType.EVALUATE, invariant = "llm.opinion"), c).valid)
    }
    @Test fun scenarioRoundTripPreservesFaultsDependenciesAndAssertions() {
        val scenario = Scenario(name = "test", faults = FaultConfig(true, true), actions = listOf(ActionIR("launch", ActionType.LAUNCH),
            ActionIR("rotate", ActionType.ROTATE, orientation = Orientation.LANDSCAPE, dependsOn = listOf("launch"), preconditions = listOf(Condition(ConditionType.ORDER_PERSISTED, operationId = "checkout-1")))),
            assertion = AssertionSpec(Oracles.UNIQUE_ORDER, "checkout-1"))
        assertEquals(scenario, ProbeJson.decodeFromString<Scenario>(ProbeJson.encodeToString(scenario)))
        assertTrue(ActionValidator.scenario(scenario).valid)
        assertFalse(ActionValidator.scenario(scenario.copy(actions = scenario.actions.reversed())).valid)
        assertEquals(setOf("scenario.json", "DroidProbeExportedRegression.kt", "README.md"), ScenarioExport.textFiles(scenario).keys)
    }
    @Test fun failureGroupingSeparatesBusinessPredicates() {
        val order = Oracles.evaluate(AssertionSpec(Oracles.UNIQUE_ORDER), checkout(AppMode.FAULTY).state)
        val draft = InvariantResult(Oracles.DRAFT, false, true, "expected", "", null)
        assertNotEquals(order.fingerprint(), draft.fingerprint())
    }
    @Test fun minimizationRejectsInvalidAndDifferentFailures() = runBlocking {
        val actions = (1..8).map { ActionIR("a$it", ActionType.BACK) }
        val scenario = Scenario(name = "longer", actions = actions, assertion = AssertionSpec(Oracles.UNIQUE_ORDER))
        val violation = InvariantResult(Oracles.UNIQUE_ORDER, false, true, "orderCount <= 1", "orderCount = 2")
        val result = Minimizer().minimize(scenario, violation.fingerprint(), maxReplays = 60) { candidate ->
            when {
                candidate.actions.none { it.id == "a2" } -> ReplayResult("r", AppMode.FAULTY, ReplayStatus.INVALID_PRECONDITION, violation)
                candidate.actions.none { it.id == "a6" } -> ReplayResult("r", AppMode.FAULTY, ReplayStatus.REPRODUCED, InvariantResult(Oracles.DRAFT, false, true, "draft", ""))
                else -> ReplayResult("r", AppMode.FAULTY, ReplayStatus.REPRODUCED, violation)
            }
        }
        assertEquals(setOf("a2", "a6"), result.scenario.actions.map { it.id }.toSet())
        assertTrue(result.attempts.any { ReplayStatus.INVALID_PRECONDITION in it.statuses && !it.kept })
    }
    @Test fun minimizationRespectsEventDependenciesAndIntermittentThreshold() = runBlocking {
        val s = Scenario(name = "dependent", actions = listOf(ActionIR("submit", ActionType.BACK), ActionIR("wait", ActionType.WAIT, condition = Condition(ConditionType.ORDER_PERSISTED), dependsOn = listOf("submit")), ActionIR("noise", ActionType.BACK)), assertion = AssertionSpec(Oracles.UNIQUE_ORDER))
        val violation = InvariantResult(Oracles.UNIQUE_ORDER, false, true, "orderCount <= 1", "orderCount = 2")
        var call = 0
        val result = Minimizer().minimize(s, violation.fingerprint(), maxReplays = 30, repeats = 3, threshold = 2) { candidate ->
            assertTrue(ActionValidator.scenario(candidate).valid)
            val hasDependencies = candidate.actions.any { it.id == "submit" } && candidate.actions.any { it.id == "wait" }
            call++
            ReplayResult("r", AppMode.FAULTY, if (hasDependencies && call % 3 != 0) ReplayStatus.REPRODUCED else ReplayStatus.FAILURE_NOT_OBSERVED, violation)
        }
        assertEquals(2, result.scenario.actions.size); assertTrue(result.attempts.filter { it.kept }.all { it.successes >= 2 && it.attempts == 3 })
    }
    @Test fun plannerMissingOrMalformedModelProgressesWithAccurateLabel() = runBlocking {
        val c = PlannerContext(observation(), listOf(ActionIR("proposal", ActionType.TAP, selector = Selector("submit"))), StateGraph(), RunConfig(), 10)
        val absent = LlmPlanner(null)
        assertNotNull(absent.propose(c)); assertTrue(absent.identity.startsWith("graph-baseline")); assertEquals(1, absent.stats.fallbacks)
        val malformed = LlmPlanner(object : LocalInference { override val modelIdentity = "fixture-test"; override suspend fun generate(prompt: String) = "not json" })
        assertNotNull(malformed.propose(c)); assertEquals(2, malformed.stats.parseFailures); assertEquals(1, malformed.stats.fallbacks); assertEquals(0, malformed.stats.modelAccepted)
        assertTrue(malformed.identity.startsWith("graph-baseline"))
    }
    @Test fun acceptedModelProposalStillComesFromValidatedVocabulary() = runBlocking {
        val c = PlannerContext(observation(), listOf(ActionIR("proposal", ActionType.TAP, selector = Selector("submit"))), StateGraph(), RunConfig(), 10)
        val planner = LlmPlanner(object : LocalInference { override val modelIdentity = "fixture-test"; override suspend fun generate(prompt: String) = """{"action":"TAP","targetKey":"submit","parameters":{"option":"0"},"rationale":"Visible enabled submit"}""" })
        assertEquals("submit", planner.propose(c)?.selector?.key); assertEquals(1, planner.stats.modelAccepted); assertEquals("local-llm-guided-graph", planner.identity)
    }
    @Test fun replayDoesNotTreatInvalidOrTimeoutAsFixed() = runBlocking {
        val driver = object : Driver {
            override suspend fun reset(runId: String, mode: AppMode, fixtures: Fixtures, faults: FaultConfig, orientation: Orientation) = Unit
            override suspend fun observe(history: List<String>) = observation()
            override suspend fun execute(action: ActionIR) { throw WorkflowTimeout("deadline") }
        }
        val s = Scenario(name = "timeout", actions = listOf(ActionIR("launch", ActionType.LAUNCH)), assertion = AssertionSpec(Oracles.UNIQUE_ORDER))
        assertEquals(ReplayStatus.TIMEOUT, ReplayEngine(driver).replay(s, "timeout").status)
        assertEquals(ReplayStatus.INVALID_PRECONDITION, ReplayEngine(driver).replay(s.copy(actions = listOf(ActionIR("tap", ActionType.TAP, selector = Selector("absent")))), "invalid").status)
    }
}
