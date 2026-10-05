package dev.droidprobe.core

import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
object StateNormalizer {
    fun signature(o: Observation): String {
        val s = o.sdk
        // Exclude run IDs, timestamps and record IDs. Retain business values and pending phases.
        val stable = listOf(o.packageName, o.screen, o.orientation, o.foreground,
            o.elements.sortedBy { it.key }.joinToString { "${it.key}:${it.enabled}:${it.value ?: ""}" },
            s?.phase, s?.orders?.groupingBy { it.operationId }?.eachCount()?.values?.sorted(),
            s?.acknowledgedDraft, s?.visibleDraft, s?.cartItems, s?.faults,
            s?.events?.takeLast(5)?.map { it.type }, o.recentActions.takeLast(3))
        return sha256(stable.joinToString("\u001f"))
    }
}
@Serializable data class GraphNode(val signature: String, val screen: String?, val phase: String?, var visits: Int = 0,
    val recentActions: List<String>, val selectedSdk: AppState?, var availableActions: List<String> = emptyList(), val failures: MutableList<String> = mutableListOf())
@Serializable data class Transition(val from: String, val to: String, val actionKey: String, val actionId: String)
@Serializable class StateGraph(val nodes: MutableMap<String, GraphNode> = linkedMapOf(), val transitions: MutableList<Transition> = mutableListOf()) {
    fun observe(o: Observation): String {
        val sig = StateNormalizer.signature(o)
        val node = nodes.getOrPut(sig) { GraphNode(sig, o.screen, o.sdk?.phase, recentActions = o.recentActions.takeLast(4), selectedSdk = o.sdk?.copy(events = emptyList())) }
        node.visits++
        return sig
    }
    fun attempts(state: String, action: String) = transitions.count { it.from == state && it.actionKey == action }
    fun connect(from: String, to: String, action: ActionIR) { transitions += Transition(from, to, action.key(), action.id) }
}
@Serializable data class SchedulerWeights(val novelty: Double = 4.0, val risk: Double = 2.0, val failure: Double = 2.0, val cost: Double = 0.5)
@Serializable data class RunConfig(val targetPackage: String = SAMPLE_PACKAGE, val goal: String = "Exercise shopping and acknowledged draft persistence under supported lifecycle and response disturbances.",
    val planner: String = "graph", val seed: Int = 1, val actionBudget: Int = 60, val wallClockMs: Long = 180_000,
    val maxStateVisits: Int = 5, val maxInvalidProposals: Int = 3, val invariants: List<String> = Oracles.approved.sorted(),
    val weights: SchedulerWeights = SchedulerWeights(), val mode: AppMode = AppMode.FAULTY, val faults: FaultConfig = FaultConfig(holdAcknowledgement = true))
data class PlannerContext(val observation: Observation, val available: List<ActionIR>, val graph: StateGraph, val config: RunConfig, val remaining: Int)
interface Planner { val identity: String; suspend fun propose(context: PlannerContext): ActionIR? }
object AvailableActions {
    fun from(o: Observation): List<ActionIR> {
        val result = mutableListOf<ActionIR>()
        fun add(action: ActionIR) { result += action }
        if (!o.foreground) return listOf(ActionIR("proposal", ActionType.REOPEN))
        o.elements.filter { it.enabled }.sortedBy { it.key }.forEach { e ->
            if (e.editable) add(ActionIR("proposal", ActionType.ENTER_TEXT, selector = Selector(e.key), text = "Probe draft ${o.sdk?.operationCounter ?: 0}"))
            else add(ActionIR("proposal", ActionType.TAP, selector = Selector(e.key)))
        }
        add(ActionIR("proposal", ActionType.ROTATE, orientation = if (o.orientation == Orientation.PORTRAIT) Orientation.LANDSCAPE else Orientation.PORTRAIT,
            preconditions = if (o.sdk?.phase == "ackPending") listOf(Condition(ConditionType.ORDER_PERSISTED, operationId = o.sdk.operationId)) else emptyList()))
        add(ActionIR("proposal", ActionType.BACKGROUND))
        if (o.screen != "Home") add(ActionIR("proposal", ActionType.BACK))
        val s = o.sdk
        if (s?.phase == "ackPending") {
            add(ActionIR("proposal", ActionType.RELEASE_FAULT))
            add(ActionIR("proposal", ActionType.WAIT, condition = Condition(ConditionType.ORDER_PERSISTED)))
        } else if (s != null) {
            add(ActionIR("proposal", ActionType.CONFIGURE_FAULT, fault = FaultConfig(holdAcknowledgement = !s.faults.holdAcknowledgement)))
            add(ActionIR("proposal", ActionType.CONFIGURE_FAULT, fault = FaultConfig(requestError = !s.faults.requestError, holdAcknowledgement = s.faults.holdAcknowledgement)))
        }
        return result
    }
}
class RandomPlanner(seed: Int) : Planner {
    private val random = Random(seed)
    override val identity = "random-baseline"
    override suspend fun propose(context: PlannerContext) = context.available.randomOrNull(random)
}
class GraphPlanner : Planner {
    override val identity = "graph-baseline"
    override suspend fun propose(context: PlannerContext): ActionIR? {
        val sig = StateNormalizer.signature(context.observation)
        val s = context.observation.sdk
        val w = context.config.weights
        fun score(a: ActionIR): Double {
            val attempts = context.graph.attempts(sig, a.key())
            val novelty = 1.0 / (1 + attempts)
            val label = context.observation.elements.find { it.key == a.selector?.key }?.label?.lowercase() ?: ""
            // Generic risk: lifecycle disturbance around in-flight writes and acknowledged data.
            val risk = when (a.type) {
                ActionType.ROTATE -> if (s?.phase == "ackPending" || s?.acknowledgedDraft != null) 3.0 else 0.0
                ActionType.TAP -> if (listOf("checkout", "submit", "save", "cart", "product").any { it in label }) 1.5 else 0.7
                ActionType.ENTER_TEXT -> if (s?.draftInput.isNullOrEmpty()) 2.0 else 0.0
                ActionType.BACKGROUND -> if (s?.phase == "ackPending") 1.0 else 0.0
                ActionType.RELEASE_FAULT -> 1.0
                else -> 0.0
            }
            val cost = if (a.type in setOf(ActionType.ROTATE, ActionType.BACKGROUND, ActionType.CONFIGURE_FAULT)) 2.0 else 0.5
            val failureRelevance = if (context.graph.nodes[sig]?.failures?.isNotEmpty() == true) 1.0 else 0.0
            return w.novelty * novelty + w.risk * risk + w.failure * failureRelevance - w.cost * cost - attempts * 3.0
        }
        return context.available.sortedBy { it.key() }.maxByOrNull { score(it) }
    }
}
interface LocalInference { val modelIdentity: String; suspend fun generate(prompt: String): String }
@Serializable data class ModelProposal(val action: String, val targetKey: String? = null, val parameters: Map<String, String> = emptyMap(), val rationale: String = "")
@Serializable data class PlannerStats(var inferenceCount: Int = 0, var inferenceMs: Long = 0, var parseFailures: Int = 0, var fallbacks: Int = 0, var modelAccepted: Int = 0, var initializationMs: Long = 0)
class LlmPlanner(private val inference: LocalInference?, private val baseline: Planner = GraphPlanner(), val stats: PlannerStats = PlannerStats()) : Planner {
    override val identity get() = if (inference == null || stats.modelAccepted == 0) "graph-baseline (local model unavailable or no accepted proposals)" else "local-llm-guided-graph"
    override suspend fun propose(context: PlannerContext): ActionIR? {
        // Do not load/infer inside a held response timing window.
        if (inference == null || context.observation.sdk?.phase == "ackPending") { stats.fallbacks++; return baseline.propose(context) }
        val options = context.available.mapIndexed { i, a -> "$i: ${a.type} target=${a.selector?.key} text=${a.text} orientation=${a.orientation} fault=${a.fault}" }
        val prompt = "Choose one supported testing action. Return only JSON with action (enum name), targetKey (nullable), parameters (string map), rationale (brief observable summary). " +
            "No commands. Goal: ${context.config.goal}. Remaining: ${context.remaining}. Approved invariants: ${context.config.invariants}. " +
            "Screen=${context.observation.screen}, phase=${context.observation.sdk?.phase}, orientation=${context.observation.orientation}, " +
            "UI=${context.observation.elements}, recent=${context.observation.recentActions.takeLast(4)}. Available:\n${options.joinToString("\n")}. " +
            "Use parameters option=the listed numeric option index."
        var repair = ""
        repeat(2) {
            val start = System.nanoTime()
            try {
                stats.inferenceCount++
                val output = inference.generate(prompt + repair).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val proposal = ProbeJson.decodeFromString<ModelProposal>(output)
                require(proposal.rationale.length <= 512)
                val a = context.available[proposal.parameters.getValue("option").toInt()]
                require(a.type.name == proposal.action && a.selector?.key == proposal.targetKey)
                val validation = ActionValidator.validate(a, ValidationContext(context.observation, context.remaining))
                require(validation.valid) { validation.reason ?: "Invalid proposal" }
                stats.modelAccepted++
                return a.copy(rationale = proposal.rationale)
            } catch (e: Exception) { stats.parseFailures++; repair = "\nPrevious proposal rejected. Return the exact schema and an available option." }
            finally { stats.inferenceMs += (System.nanoTime() - start) / 1_000_000 }
        }
        stats.fallbacks++
        return baseline.propose(context)
    }
}
