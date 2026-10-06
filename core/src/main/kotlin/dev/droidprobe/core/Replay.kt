package dev.droidprobe.core

import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException

@Serializable enum class ReplayStatus { REPRODUCED, FAILURE_NOT_OBSERVED, INVALID_PRECONDITION, INFRASTRUCTURE_FAILURE, TIMEOUT }
@Serializable data class ActionRecord(val action: ActionIR, val beforeSequence: Long, val afterSequence: Long, val elapsedMs: Long, val events: List<AppEvent>)
@Serializable data class ReplayResult(val runId: String, val mode: AppMode, val status: ReplayStatus, val assertion: InvariantResult? = null,
    val records: List<ActionRecord> = emptyList(), val observations: List<Observation> = emptyList(), val detail: String? = null, val elapsedMs: Long = 0)
class PreconditionFailure(message: String) : Exception(message)
class WorkflowTimeout(message: String) : Exception(message)
interface Driver {
    suspend fun reset(runId: String, mode: AppMode, fixtures: Fixtures, faults: FaultConfig, orientation: Orientation)
    suspend fun observe(history: List<String> = emptyList()): Observation
    suspend fun execute(action: ActionIR)
}
class ReplayEngine(private val driver: Driver, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    suspend fun replay(scenario: Scenario, runId: String, mode: AppMode = scenario.mode, wallClockMs: Long = 120_000): ReplayResult {
        val start = clock()
        val records = mutableListOf<ActionRecord>()
        val observations = mutableListOf<Observation>()
        var stage = "fixture reset"
        fun result(status: ReplayStatus, detail: String? = null, assertion: InvariantResult? = null) = ReplayResult(runId, mode, status, assertion, records.toList(), observations.toList(), detail, clock() - start)
        val schema = ActionValidator.scenario(scenario)
        if (!schema.valid) return result(ReplayStatus.INVALID_PRECONDITION, schema.reason)
        try {
            driver.reset(runId, mode, scenario.fixtures, scenario.faults, scenario.initialOrientation)
            stage = "initial observation"
            var o = driver.observe()
            observations += o
            val executed = mutableSetOf<String>()
            for (a in scenario.actions) {
                if (clock() - start > wallClockMs) return result(ReplayStatus.TIMEOUT, "Replay wall-clock budget exhausted")
                val v = ActionValidator.validate(a, ValidationContext(o, scenario.actions.size - records.size, executed, maxRepetitions = Int.MAX_VALUE))
                if (!v.valid) return result(ReplayStatus.INVALID_PRECONDITION, "${a.id}: ${v.reason}")
                val actionStart = clock()
                stage = "action ${a.id} (${a.type})"
                driver.execute(a)
                stage = "observation after ${a.id}"
                val next = driver.observe((records.map { it.action.key() } + a.key()).takeLast(4))
                records += ActionRecord(a, o.sequence, next.sequence, clock() - actionStart, next.sdk?.events?.filter { it.sequence > o.synchronizedThroughEvent } ?: emptyList())
                observations += next
                executed += a.id
                o = next
            }
            if (clock() - start > wallClockMs) return result(ReplayStatus.TIMEOUT, "Replay wall-clock budget exhausted after action completion")
            val sdk = o.sdk ?: return result(ReplayStatus.INFRASTRUCTURE_FAILURE, "Missing SDK business assertion signals")
            val invariant = Oracles.evaluate(scenario.assertion, sdk)
            if (!invariant.applicable) return result(ReplayStatus.INVALID_PRECONDITION, "Assertion has no applicable logical operation/acknowledged data", invariant)
            return result(if (invariant.passed) ReplayStatus.FAILURE_NOT_OBSERVED else ReplayStatus.REPRODUCED, assertion = invariant)
        } catch (e: PreconditionFailure) { return result(ReplayStatus.INVALID_PRECONDITION, e.message) }
        catch (e: WorkflowTimeout) { return result(ReplayStatus.TIMEOUT, e.message) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { return result(ReplayStatus.INFRASTRUCTURE_FAILURE, "$stage: ${e.stackTraceToString()}") }
    }
}
@Serializable data class MinimizeAttempt(val length: Int, val successes: Int, val attempts: Int, val statuses: List<ReplayStatus>, val kept: Boolean)
@Serializable data class MinimizeResult(val originalLength: Int, val scenario: Scenario, val attempts: List<MinimizeAttempt>, val replayCount: Int, val budgetExpired: Boolean,
    val description: String = "Smallest reproduction found within the configured search budget")
class Minimizer {
    suspend fun minimize(scenario: Scenario, fingerprint: String, maxReplays: Int = 40, repeats: Int = 1, threshold: Int = repeats,
        wallClockMs: Long = 180_000, replay: suspend (Scenario) -> ReplayResult): MinimizeResult {
        require(repeats in 1..10 && threshold in 1..repeats && maxReplays > 0)
        val start = System.nanoTime()
        var current = scenario
        var partitions = 2
        var used = 0
        val attempts = mutableListOf<MinimizeAttempt>()
        fun outOfTime() = (System.nanoTime() - start) / 1_000_000 > wallClockMs
        while (current.actions.size >= 2 && used + repeats <= maxReplays && !outOfTime()) {
            val chunkSize = (current.actions.size + partitions - 1) / partitions
            var reduced = false
            for (offset in current.actions.indices step chunkSize) {
                if (used + repeats > maxReplays || outOfTime()) break
                val removed = current.actions.drop(offset).take(chunkSize).map { it.id }.toSet()
                val actions = current.actions.filter { it.id !in removed }
                if (actions.isEmpty() || actions.any { a -> a.dependsOn.any { it in removed } }) continue
                val candidate = current.copy(actions = actions)
                val outcomes = mutableListOf<ReplayResult>()
                repeat(repeats) { outcomes += replay(candidate); used++ }
                val successes = outcomes.count { it.status == ReplayStatus.REPRODUCED && it.assertion?.fingerprint() == fingerprint }
                val kept = successes >= threshold
                attempts += MinimizeAttempt(actions.size, successes, outcomes.size, outcomes.map { it.status }, kept)
                if (kept) { current = candidate; partitions = maxOf(2, partitions - 1); reduced = true; break }
            }
            if (!reduced) { if (partitions >= current.actions.size) break; partitions = minOf(current.actions.size, partitions * 2) }
        }
        return MinimizeResult(scenario.actions.size, current, attempts, used, used + repeats > maxReplays || outOfTime())
    }
}
