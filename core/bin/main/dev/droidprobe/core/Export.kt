package dev.droidprobe.core

import kotlinx.serialization.encodeToString

object ScenarioExport {
    fun textFiles(scenario: Scenario): Map<String, String> {
        require(ActionValidator.scenario(scenario).valid)
        return linkedMapOf(
            "scenario.json" to ProbeJson.encodeToString(scenario),
            "DroidProbeExportedRegression.kt" to """
                package dev.droidprobe.runner
                import androidx.test.ext.junit.runners.AndroidJUnit4
                import org.junit.Test
                import org.junit.runner.RunWith
                @RunWith(AndroidJUnit4::class)
                class DroidProbeExportedRegression {
                    @Test fun businessInvariant() = RegressionHarness.assertAsset("scenario.json")
                }
            """.trimIndent(),
            "README.md" to """
                # DroidProbe regression bundle
                Requires DroidProbe replay harness **0.1.0**, AndroidX Test runner 1.7.0,
                JUnit extension 1.3.0 and UI Automator 2.3.0; uses debug sample/runner signed alike.
                ActionIR v1 and scenario.json are the source of truth. No planner runs during replay.

                From the DroidProbe repository:
                ```powershell
                ./scripts/Import-Export.ps1 -BundleDirectory <this-extracted-bundle>
                ./gradlew.bat :sample-app:installDebug :runner:installDebug :runner:assembleDebugAndroidTest
                adb install -r runner/build/outputs/apk/androidTest/debug/runner-debug-androidTest.apk
                adb shell am instrument -w -e class dev.droidprobe.runner.DroidProbeExportedRegression -e mode CORRECTED dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner
                ```
                Substitute FAULTY to verify that this same assertion fails. Check the JUnit result,
                not adb's process exit code. A timeout or invalid precondition is not a passing fix.
                Evidence/replay outcomes accompany the bundle; screenshots are PNG files when captured.
            """.trimIndent())
    }
}
