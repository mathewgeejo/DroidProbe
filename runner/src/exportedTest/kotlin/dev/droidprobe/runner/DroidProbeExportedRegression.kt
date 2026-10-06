package dev.droidprobe.runner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DroidProbeExportedRegression {
    @Test fun businessInvariant() = RegressionHarness.assertAsset("scenario.json")
}