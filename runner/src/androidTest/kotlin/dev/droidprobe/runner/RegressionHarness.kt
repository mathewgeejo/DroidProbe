package dev.droidprobe.runner

import androidx.test.platform.app.InstrumentationRegistry
import dev.droidprobe.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import java.io.File

/** Versioned harness 0.1.0: a regression passes only when the business assertion ran and passed. */
object RegressionHarness {
    fun assertAsset(name: String) = runBlocking(Dispatchers.Default) {
        require(name == "scenario.json")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = instrumentation.context.assets.open(name).bufferedReader().use { ProbeJson.decodeFromString<Scenario>(it.readText()) }
        val mode = InstrumentationRegistry.getArguments().getString("mode")?.let { AppMode.valueOf(it) } ?: AppMode.CORRECTED
        val driver = AndroidDriver(File(instrumentation.targetContext.filesDir, "exported-evidence"))
        try {
            val result = ReplayEngine(driver).replay(scenario, "exported-${System.currentTimeMillis()}", mode)
            assertEquals("${result.detail ?: ""} ${result.assertion}", ReplayStatus.FAILURE_NOT_OBSERVED, result.status)
            check(result.assertion?.applicable == true && result.assertion.passed) { "Business assertion did not execute" }
        } finally { driver.cleanup() }
    }
}
