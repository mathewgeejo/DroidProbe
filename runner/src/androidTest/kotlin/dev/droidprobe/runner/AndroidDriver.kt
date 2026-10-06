package dev.droidprobe.runner

import android.content.Intent
import android.os.SystemClock
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.droidprobe.core.*
import dev.droidprobe.sdk.*
import java.io.File
import kotlinx.coroutines.delay

/** Only this instrumented environment can use UiDevice. Shell text is a fixed fixture-reset command. */
class AndroidDriver(private val evidenceDir: File) : Driver {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val client = ProbeClient(context.contentResolver)
    private val device = UiDevice.getInstance(instrumentation)
    private var runId = "uninitialized"
    private var sequence = 0L
    private var lastScreen: String? = null
    private var lastGeneration = 0
    private var dirtyUi = true
    private var startOrientation: Orientation? = null
    private fun setOrientation(orientation: Orientation) {
        // Android 11+ supports explicit display rotation through UI Automator's
        // window-manager API. Older devices use the UiAutomation default display.
        if (Build.VERSION.SDK_INT >= 30) {
            if (orientation == Orientation.PORTRAIT) device.setOrientationNatural(0) else device.setOrientationLeft(0)
        } else {
            if (orientation == Orientation.PORTRAIT) device.setOrientationNatural() else device.setOrientationLeft()
        }
    }
    suspend fun state(): AppState = client.exchange(BridgeRequest(BridgeCommand.READ, runId))
    override suspend fun reset(runId: String, mode: AppMode, fixtures: Fixtures, faults: FaultConfig, orientation: Orientation) {
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        // Force-stop is reset plumbing, not a simulated OS process-death test.
        device.executeShellCommand("am force-stop dev.droidprobe.sample")
        if (this.runId != runId) sequence = 0
        this.runId = runId; lastScreen = null; lastGeneration = 0; dirtyUi = true
        startOrientation = orientation
        device.pressHome()
        setOrientation(orientation)
        client.exchange(BridgeRequest(BridgeCommand.RESET, runId, mode, fixtures, faults))
    }
    private suspend fun awaitCondition(timeoutMs: Long, description: String, predicate: suspend () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) { if (predicate()) return; delay(50) }
        throw WorkflowTimeout(description)
    }
    private suspend fun launch(timeoutMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(SAMPLE_PACKAGE)) { "Sample APK is not installed" }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        if (!device.wait(Until.hasObject(By.pkg(SAMPLE_PACKAGE)), timeoutMs)) throw WorkflowTimeout("Target package did not become visible")
        awaitCondition(maxOf(1, deadline - SystemClock.elapsedRealtime()), "Target did not resume") { state().foreground }
        startOrientation?.let { orientation ->
            setOrientation(orientation)
            awaitCondition(maxOf(1, deadline - SystemClock.elapsedRealtime()), "Target starting orientation was not restored") {
                device.displayRotation == if (orientation == Orientation.PORTRAIT) 0 else 1
            }
            startOrientation = null
        }
        dirtyUi = true
    }
    override suspend fun execute(action: ActionIR) {
        require(action.packageName == SAMPLE_PACKAGE)
        when (action.type) {
            ActionType.LAUNCH, ActionType.REOPEN -> launch(action.timeoutMs)
            ActionType.TAP, ActionType.ENTER_TEXT -> {
                val key = requireNotNull(action.selector).key
                val node = device.wait(Until.findObject(By.res(key).pkg(SAMPLE_PACKAGE)), action.timeoutMs)
                    ?: throw PreconditionFailure("UI selector $key not reachable")
                if (!node.isEnabled) throw PreconditionFailure("Selector $key disabled")
                if (node.visibleBounds.isEmpty) throw PreconditionFailure("Selector $key has no reachable visible bounds")
                if (action.type == ActionType.TAP) node.click() else { node.text = requireNotNull(action.text) }
                dirtyUi = true
            }
            ActionType.BACK -> { device.pressBack(); dirtyUi = true }
            ActionType.BACKGROUND -> { device.pressHome(); awaitCondition(action.timeoutMs, "Target did not background") { !state().foreground }; dirtyUi = true }
            ActionType.ROTATE -> {
                val before = state().generation
                val rotation = if (action.orientation == Orientation.PORTRAIT) 0 else 1
                if (device.displayRotation != rotation) {
                    setOrientation(requireNotNull(action.orientation))
                    awaitCondition(action.timeoutMs, "Rotation did not recreate target activity") { state().generation > before && state().foreground }
                }
                dirtyUi = true
            }
            ActionType.WAIT -> awaitCondition(action.timeoutMs, "Condition timed out: ${action.condition}") { requireNotNull(action.condition).matches(state(), device.currentPackageName == SAMPLE_PACKAGE) }
            ActionType.CONFIGURE_FAULT -> client.exchange(BridgeRequest(BridgeCommand.CONFIGURE_FAULT, runId, faults = requireNotNull(action.fault)))
            ActionType.RELEASE_FAULT -> { client.exchange(BridgeRequest(BridgeCommand.RELEASE_FAULT, runId)); dirtyUi = true }
            ActionType.EVALUATE -> Oracles.evaluate(AssertionSpec(requireNotNull(action.invariant)), state())
        }
    }
    override suspend fun observe(history: List<String>): Observation {
        if (dirtyUi) { device.waitForIdle(3_000); dirtyUi = false }
        var s = state()
        var foreground = device.currentPackageName == SAMPLE_PACKAGE && s.foreground
        if (foreground && (dirtyUi || s.screen != lastScreen || s.generation != lastGeneration)) {
            // UI synchronization, distinct from a business event wait.
            device.waitForIdle(2_000)
            s = state()
            foreground = device.currentPackageName == SAMPLE_PACKAGE && s.foreground
        }
        val nodes = if (foreground) device.findObjects(By.pkg(SAMPLE_PACKAGE)) else emptyList()
        val elements = nodes.filter { !it.resourceName.isNullOrBlank() && !it.visibleBounds.isEmpty && (it.isClickable || it.className == "android.widget.EditText") }
            .map { UiElement(it.resourceName, it.text ?: it.contentDescription ?: it.children.mapNotNull { child -> child.text }.joinToString(" "), it.isEnabled,
                it.className == "android.widget.EditText", if (it.className == "android.widget.EditText") it.text else null) }.distinctBy { it.key }
        sequence++
        val screenshotName = "$runId-$sequence.png"
        evidenceDir.mkdirs()
        val captured = device.takeScreenshot(File(evidenceDir, screenshotName))
        lastScreen = s.screen; lastGeneration = s.generation
        return Observation(runId, sequence, SAMPLE_PACKAGE, activity = if (foreground) "dev.droidprobe.sample.MainActivity" else null,
            screen = s.screen, orientation = if (device.displayRotation == 0) Orientation.PORTRAIT else Orientation.LANDSCAPE,
            foreground = foreground, elements = elements, recentActions = history, sdk = s, screenshot = if (captured) screenshotName else null,
            elapsedMs = SystemClock.elapsedRealtime(), synchronizedThroughEvent = s.events.lastOrNull()?.sequence ?: 0,
            limitations = if (foreground && elements.isEmpty()) listOf("No actionable stable accessibility selectors; custom drawing is unsupported") else emptyList())
    }
    fun cleanup() {
        if (Build.VERSION.SDK_INT >= 30) device.unfreezeRotation(0) else device.unfreezeRotation()
        device.pressHome()
    }
}
