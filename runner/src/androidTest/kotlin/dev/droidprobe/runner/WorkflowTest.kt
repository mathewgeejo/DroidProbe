package dev.droidprobe.runner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.droidprobe.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @Test fun checkoutReplayConfirmsFaultyAndCorrected() = runBlocking(Dispatchers.Default) {
        val store = RunStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val driver = AndroidDriver(File(store.directory("vertical-slice"), "screenshots"))
        try {
            val engine = ReplayEngine(driver)
            val faulty = engine.replay(FixtureScenarios.checkout(), "checkout-faulty", AppMode.FAULTY)
            assertEquals(faulty.detail, ReplayStatus.REPRODUCED, faulty.status)
            assertEquals("orderCount = 2", faulty.assertion?.observed)
            assertTrue(faulty.records.flatMap { it.events }.any { it.type == "lifecycleResubmission" })
            val repeat = engine.replay(FixtureScenarios.checkout(), "checkout-repeat", AppMode.FAULTY)
            assertEquals(repeat.detail, ReplayStatus.REPRODUCED, repeat.status)
            val fixed = engine.replay(FixtureScenarios.checkout(), "checkout-corrected", AppMode.CORRECTED)
            assertEquals(fixed.detail, ReplayStatus.FAILURE_NOT_OBSERVED, fixed.status)
            assertEquals("orderCount = 1", fixed.assertion?.observed)
            assertTrue(fixed.assertion!!.passed)
            val config = RunConfig()
            store.save(RunReport("vertical-slice", config, "scripted reproduction fixture", "validated checkout replay", faulty.records, faulty.observations,
                StateGraph(), listOf(Finding(fingerprint = faulty.assertion!!.fingerprint(), assertion = faulty.assertion!!, actionIndex = faulty.records.lastIndex, screenshot = faulty.observations.last().screenshot)),
                emptyList(), faulty.elapsedMs, replays = listOf(faulty, repeat, fixed), device = deviceMetadata()))
        } finally { driver.cleanup() }
    }
    @Test fun acknowledgedDraftRecreation() = runBlocking(Dispatchers.Default) {
        val driver = AndroidDriver(File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "draft-evidence"))
        try {
            val replay = ReplayEngine(driver)
            val faulty = replay.replay(FixtureScenarios.draft(), "draft-faulty", AppMode.FAULTY)
            assertEquals(faulty.detail, ReplayStatus.REPRODUCED, faulty.status)
            val fixed = replay.replay(FixtureScenarios.draft(), "draft-fixed", AppMode.CORRECTED)
            assertEquals(fixed.detail, ReplayStatus.FAILURE_NOT_OBSERVED, fixed.status)
        } finally { driver.cleanup() }
    }
    @Test fun controlledRequestErrorAndCleanReset() = runBlocking(Dispatchers.Default) {
        val driver = AndroidDriver(File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "reset-evidence"))
        try {
            val scenario = FixtureScenarios.checkout().copy(faults = FaultConfig(requestError = true), actions = FixtureScenarios.checkout().actions.take(5) +
                ActionIR("error", ActionType.WAIT, condition = Condition(ConditionType.REQUEST_ERROR)), assertion = AssertionSpec(Oracles.ERROR))
            val result = ReplayEngine(driver).replay(scenario, "request-error")
            assertEquals(result.detail, ReplayStatus.FAILURE_NOT_OBSERVED, result.status)
            assertTrue(driver.state().orders.isEmpty())
            driver.reset("clean-reset", AppMode.CORRECTED, Fixtures(), FaultConfig(), Orientation.PORTRAIT)
            val clean = driver.state()
            assertNull(clean.operationId); assertTrue(clean.orders.isEmpty()); assertNull(clean.acknowledgedDraft)
            assertTrue(clean.events.all { it.runId == "clean-reset" })
            assertEquals(1, clean.events.size)
        } finally { driver.cleanup() }
    }
}
