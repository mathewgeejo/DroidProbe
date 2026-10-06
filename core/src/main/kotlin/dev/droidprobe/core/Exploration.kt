package dev.droidprobe.core

import kotlinx.serialization.Serializable

@Serializable data class Finding(val classification: String = "confirmed invariant violation", val fingerprint: String, val assertion: InvariantResult, val actionIndex: Int, val screenshot: String?)
@Serializable data class RunReport(val runId: String, val config: RunConfig, val plannerIdentity: String, val status: String,
    val records: List<ActionRecord>, val observations: List<Observation>, val graph: StateGraph, val findings: List<Finding>,
    val rejections: List<String>, val elapsedMs: Long, val modelIdentity: String? = null, val modelStatus: String = "Baseline selected",
    val plannerStats: PlannerStats = PlannerStats(), val device: Map<String, String> = emptyMap(),
    val replays: List<ReplayResult> = emptyList(), val minimization: MinimizeResult? = null, val exportedBundle: String? = null,
    val buildVersion: String = "0.1.0", val fixtures: Fixtures = Fixtures(), val previousEpisodes: List<List<ActionRecord>> = emptyList(), val totalExecutedActions: Int = records.size)
class Explorer(private val driver: Driver, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }, private val progress: (String) -> Unit = {}) {
    suspend fun run(runId: String, config: RunConfig, planner: Planner): RunReport {
        require(config.targetPackage == SAMPLE_PACKAGE && config.actionBudget in 1..500 && config.wallClockMs in 1_000..3_600_000)
        require(config.invariants.isNotEmpty() && config.invariants.all { it in Oracles.approved })
        val start = clock()
        val records = mutableListOf<ActionRecord>(); val observations = mutableListOf<Observation>(); val findings = mutableListOf<Finding>()
        val graph = StateGraph(); val rejected = mutableListOf<String>(); val executed = mutableSetOf<String>(); val reps = mutableMapOf<String, Int>()
        var status = "budget exhausted"; var invalid = 0; var resets = 0; var totalActions = 0
        val previousEpisodes = mutableListOf<List<ActionRecord>>()
        try {
            driver.reset(runId, config.mode, Fixtures(), config.faults, Orientation.PORTRAIT)
            // Launch is itself replayable and never chosen by an LLM during reset.
            val launch = ActionIR("a0", ActionType.LAUNCH, timeoutMs = 30_000)
            val initial = driver.observe(); driver.execute(launch)
            var o = driver.observe(listOf(launch.key()))
            records += ActionRecord(launch, initial.sequence, o.sequence, clock() - start, o.sdk?.events ?: emptyList())
            totalActions++
            observations += initial; observations += o; executed += launch.id
            var sig = graph.observe(o)
            while (totalActions < config.actionBudget && clock() - start < config.wallClockMs) {
                if ((graph.nodes[sig]?.visits ?: 0) > config.maxStateVisits) {
                    // End the current episode. A fresh fixture reset starts a new replayable history.
                    if (++resets > 2) { status = "navigation loop limit"; break }
                    driver.reset(runId, config.mode, Fixtures(), config.faults, Orientation.PORTRAIT)
                    previousEpisodes += records.toList()
                    records.clear(); executed.clear(); reps.clear()
                    driver.execute(launch); o = driver.observe(); executed += launch.id
                    records += ActionRecord(launch, o.sequence, o.sequence, 0, o.sdk?.events ?: emptyList()); observations += o; sig = graph.observe(o)
                    totalActions++
                    if (totalActions >= config.actionBudget) break
                }
                val available = AvailableActions.from(o).filter { ActionValidator.validate(it, ValidationContext(o, config.actionBudget - totalActions, repetitions = reps)).valid }
                graph.nodes[sig]?.availableActions = available.map { it.key() }
                if (available.isEmpty()) { status = "no reachable actions"; break }
                val proposal = planner.propose(PlannerContext(o, available, graph, config, config.actionBudget - totalActions)) ?: break
                val a = proposal.copy(id = "a${executed.size}")
                val v = ActionValidator.validate(a, ValidationContext(o, config.actionBudget - totalActions, executed, reps))
                if (!v.valid) {
                    rejected += "${a.key()}: ${v.reason}"; invalid++
                    if (invalid >= config.maxInvalidProposals) { status = "invalid proposal limit"; break }; continue
                }
                invalid = 0
                val actionStart = clock()
                try { driver.execute(a) } catch (e: PreconditionFailure) {
                    rejected += "Unreachable ${a.key()}: ${e.message}"; reps[a.key()] = 4; continue
                }
                val next = driver.observe((records.map { it.action.key() } + a.key()).takeLast(4))
                records += ActionRecord(a, o.sequence, next.sequence, clock() - actionStart, next.sdk?.events?.filter { it.sequence > o.synchronizedThroughEvent } ?: emptyList())
                totalActions++
                observations += next; executed += a.id; reps[a.key()] = (reps[a.key()] ?: 0) + 1
                val nextSig = graph.observe(next); graph.connect(sig, nextSig, a); sig = nextSig; o = next
                config.invariants.forEach { id ->
                    val result = next.sdk?.let { Oracles.evaluate(AssertionSpec(id), it) }
                    if (result != null && result.applicable && !result.passed && findings.none { it.fingerprint == result.fingerprint() }) {
                        findings += Finding(fingerprint = result.fingerprint(), assertion = result, actionIndex = records.lastIndex, screenshot = next.screenshot)
                        graph.nodes[sig]?.failures?.add(result.fingerprint())
                    }
                }
                progress("{\"runId\":\"$runId\",\"actions\":${records.size},\"states\":${graph.nodes.size},\"findings\":${findings.size}}")
                if (findings.isNotEmpty()) { status = "confirmed invariant violation"; break }
            }
        } catch (e: WorkflowTimeout) { status = "suspected stalled workflow: ${e.message}" }
        catch (e: Exception) { status = "infrastructure failure: ${e.javaClass.simpleName}: ${e.message}" }
        return RunReport(runId, config, planner.identity, status, records, observations, graph, findings, rejected, clock() - start,
            plannerStats = (planner as? LlmPlanner)?.stats ?: PlannerStats(), previousEpisodes = previousEpisodes, totalExecutedActions = totalActions)
    }
}
