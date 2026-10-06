package dev.droidprobe.runner

import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.droidprobe.core.*
import dev.droidprobe.model.LocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

fun deviceMetadata(): Map<String, String> {
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val info = ActivityManager.MemoryInfo(); ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
    return mapOf("manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT.toString(), "abis" to Build.SUPPORTED_ABIS.joinToString(), "buildFingerprint" to Build.FINGERPRINT,
        "availableMemoryBytes" to info.availMem.toString(), "totalMemoryBytes" to info.totalMem.toString(),
        "runnerPssKb" to Debug.getPss().toString(), "thermalStatus" to if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(PowerManager::class.java).currentThermalStatus.toString() else "unavailable")
}
@RunWith(AndroidJUnit4::class)
class ExplorationTest {
    @Test fun exploreConfigured() = runBlocking(Dispatchers.Default) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments(); val store = RunStore(context)
        val base = store.config()
        val config = base.copy(planner = args.getString("planner") ?: base.planner, seed = args.getString("seed")?.toInt() ?: base.seed,
            actionBudget = args.getString("budget")?.toInt() ?: base.actionBudget, mode = args.getString("mode")?.let { AppMode.valueOf(it) } ?: base.mode)
        require(config.planner in setOf("random", "graph", "local"))
        val runId = "explore-${config.planner}-${config.seed}-${System.currentTimeMillis()}"
        val driver = AndroidDriver(File(store.directory(runId), "screenshots"))
        var model: LocalModel? = null
        var status = "Baseline explicitly selected"
        val initStart = System.nanoTime()
        try {
            if (config.planner == "local") { val loaded = LocalModel.open(context); model = loaded.first; status = loaded.second.description }
            val planner: Planner = when (config.planner) { "random" -> RandomPlanner(config.seed); "local" -> LlmPlanner(model); else -> GraphPlanner() }
            if (planner is LlmPlanner) planner.stats.initializationMs = (System.nanoTime() - initStart) / 1_000_000
            var report = Explorer(driver, progress = { Log.i("DroidProbe", it) }).run(runId, config, planner)
                .copy(device = deviceMetadata(), modelIdentity = model?.modelIdentity, modelStatus = status)
            store.save(report)
            val finding = report.findings.firstOrNull()
            if (finding != null) {
                val scenario = Scenario(name = "Observed ${finding.assertion.id}", mode = config.mode, faults = config.faults,
                    actions = report.records.map { it.action }, assertion = AssertionSpec(finding.assertion.id, finding.assertion.operationId), source = "autonomous ${report.plannerIdentity}")
                val replay = ReplayEngine(driver)
                val faulty = replay.replay(scenario, "${runId}-replay", config.mode)
                val fixed = replay.replay(scenario, "${runId}-fixed", AppMode.CORRECTED)
                report = report.copy(replays = listOf(faulty, fixed)); store.save(report)
                var minimizationAttempt = 0
                val minimized = if (faulty.status == ReplayStatus.REPRODUCED) Minimizer().minimize(scenario, finding.fingerprint,
                    maxReplays = 12, wallClockMs = 120_000) { replay.replay(it, "${runId}-min-${++minimizationAttempt}", config.mode) } else null
                report = report.copy(minimization = minimized); store.save(report)
                val bundle = store.export(report, minimized?.scenario ?: scenario); store.save(report.copy(exportedBundle = bundle.name))
            }
            assertFalse(report.status, report.status.startsWith("infrastructure failure"))
        } finally { model?.close(); driver.cleanup() }
    }
}
@RunWith(AndroidJUnit4::class)
class DemoTest {
    @Test fun discoverMinimizeAndExport() = runBlocking(Dispatchers.Default) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RunStore(context); val id = "demo-${System.currentTimeMillis()}"
        val driver = AndroidDriver(File(store.directory(id), "screenshots"))
        try {
            val config = RunConfig(planner = "graph", actionBudget = 40)
            var report = Explorer(driver, progress = { Log.i("DroidProbe", it) }).run(id, config, GraphPlanner()).copy(device = deviceMetadata())
            store.save(report)
            assertTrue("Autonomous baseline did not discover an invariant violation: ${report.status}", report.findings.isNotEmpty())
            val engine = ReplayEngine(driver)
            val discovery = Scenario(name = "Autonomously observed checkout", faults = config.faults, actions = report.records.map { it.action },
                assertion = AssertionSpec(report.findings.first().assertion.id, report.findings.first().assertion.operationId), source = "autonomous graph baseline")
            val reproduced = engine.replay(discovery, "${id}-discovery-replay")
            report = report.copy(replays = listOf(reproduced)); store.save(report)
            assertEquals(reproduced.detail, ReplayStatus.REPRODUCED, reproduced.status)
            // Intentionally longer development reproduction, distinguished from autonomous discovery.
            val longer = FixtureScenarios.checkout(longer = true)
            val original = engine.replay(longer, "${id}-long")
            report = report.copy(replays = report.replays + original); store.save(report)
            assertEquals(original.detail, ReplayStatus.REPRODUCED, original.status)
            var attempt = 0
            val minimized = Minimizer().minimize(longer, original.assertion!!.fingerprint(), maxReplays = 24, wallClockMs = 300_000) {
                engine.replay(it, "${id}-min-${++attempt}")
            }
            assertTrue("No valid reduction found", minimized.scenario.actions.size < longer.actions.size)
            val faulty = engine.replay(minimized.scenario, "${id}-faulty")
            report = report.copy(minimization = minimized, replays = report.replays + faulty); store.save(report)
            val fixed = engine.replay(minimized.scenario, "${id}-fixed", AppMode.CORRECTED)
            report = report.copy(replays = report.replays + fixed); store.save(report)
            assertEquals(faulty.detail, ReplayStatus.REPRODUCED, faulty.status)
            assertEquals(fixed.detail, ReplayStatus.FAILURE_NOT_OBSERVED, fixed.status)
            report = report.copy(minimization = minimized, replays = listOf(reproduced, original, faulty, fixed)); store.save(report)
            val zip = store.export(report, minimized.scenario); store.save(report.copy(exportedBundle = zip.name))
            Log.i("DroidProbe", "{\"export\":\"${zip.absolutePath}\",\"original\":${longer.actions.size},\"minimized\":${minimized.scenario.actions.size}}")
            Unit
        } finally { driver.cleanup() }
    }
}
