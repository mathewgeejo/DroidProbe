# DroidProbe

An Android developer tool for observing a resettable sample app, exploring supported actions, confirming business failures, replaying them, reducing their reproduction sequences and exporting regression tests.

The sample and runner are separate Android packages. Automation runs through **AndroidJUnitRunner + UI Automator**, launched by authorized ADB. The dark Compose controller stores configuration and reviews actual completed results. It cannot start privileged instrumentation by itself.

See [validation and implementation status](docs/Validation.md) for what was exercised and what remains blocked. On-device LiteRT-LM inference is implemented; physical-device/model validation remains required before calling the AI-guided MVP complete.

The verified emulator workflow passed 20 JVM tests and three native workflow tests. Graph exploration discovered the checkout defect in 7 actions, and a separate longer reproduction reduced from 11 to 9 actions. The compiled exported regression passed in corrected mode and failed on the intended business assertion in faulty mode. [Captured evidence](docs/evidence/README.md) includes the raw reports, JUnit output and APK hashes.

## Build and install

Requires JDK 17 or newer compatible with Gradle 9.4.0, Android SDK platform 35, Build Tools 36.0.0, platform-tools and a supported emulator or USB-debugging device. This workspace uses JDK 25.0.1. Set `ANDROID_HOME` or put your SDK location in an untracked `local.properties`.

```powershell
$env:ANDROID_HOME = 'C:/Users/mathew/AppData/Local/Android/Sdk'
$env:PATH = "$env:ANDROID_HOME/platform-tools;$env:PATH"
./gradlew.bat :core:test :sample-app:assembleDebug :runner:assembleDebug :runner:assembleDebugAndroidTest
adb install -r sample-app/build/outputs/apk/debug/sample-app-debug.apk
adb install -r runner/build/outputs/apk/debug/runner-debug.apk
adb install -r runner/build/outputs/apk/androidTest/debug/runner-debug-androidTest.apk
```

Both debug apps use the same development key by default. No root is required. Use an emulator with orientation sensors and a phone-sized display for the rotation demonstration. No model is needed for graph/random baseline runs. Milestone builds are intentional validation steps; no automatic build runs after each edit.

## Run the workflow

```powershell
# First vertical slice: faulty replay, repeatability, corrected mode, draft and reset tests
adb shell am instrument -w -e class dev.droidprobe.runner.WorkflowTest dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner
# Autonomous baseline discovery, replay, longer-fixture minimization and export
adb shell am instrument -w -e class dev.droidprobe.runner.DemoTest dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner
# Configured exploration; controller configuration supplies defaults
adb shell am instrument -w -e class dev.droidprobe.runner.ExplorationTest -e planner graph -e budget 40 -e seed 1 dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner
# Review after execution
adb shell am start -n dev.droidprobe.runner/.MainActivity
```

JUnit's `OK (...)`/failure output is authoritative; ADB can exit zero even when a test fails. Watch progress with `adb logcat -s DroidProbe:I`. Screenshots and atomic JSON reports are stored under runner `files/runs/<runId>`. Open the controller after instrumentation finishes to avoid interrupting the target. Overview summarizes results, Runs provides search and filters, and Setup configures the next run. Each run opens Evidence, Graph, Reduce and Export views. See the [controller UI and screenshots](docs/UI.md).

## Export and regression

```powershell
python scripts/device.py pull-latest-export
# Use the printed extracted bundle directory
./scripts/Import-Export.ps1 -BundleDirectory .local/device-results/<runId>/export
./gradlew.bat :runner:assembleDebugAndroidTest
adb install -r runner/build/outputs/apk/androidTest/debug/runner-debug-androidTest.apk
adb shell am instrument -w -e class dev.droidprobe.runner.DroidProbeExportedRegression -e mode CORRECTED dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class dev.droidprobe.runner.DroidProbeExportedRegression -e mode FAULTY dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner
```

The exported Kotlin depends on **DroidProbe replay harness 0.1.0**. The corrected invocation should pass the business assertion; the faulty invocation should fail it. Replay does not ask the planner again. `scenario.json` includes versioned ActionIR, initial fixtures/orientation/mode, fault settings, dependencies/preconditions and the assertion. The ZIP also includes Kotlin, README, JSON evidence and captured PNGs. Timeouts, invalid preconditions and infrastructure errors never become “bug fixed.”

The verified nine-action scenario and its generated Kotlin class are retained in `runner/src/exportedTest`. AGP's built-in Kotlin source set includes this directory in the instrumentation APK; importing a new bundle replaces those two artifacts.

## Evaluation and documentation

```powershell
./scripts/Evaluate.ps1 -Seeds 1,2,3 -Budget 40
# After installing and validating a physical-device model:
./scripts/Evaluate.ps1 -Seeds 1,2,3 -Budget 40 -IncludeLocal
python scripts/device.py pull-runs
```

Compare only matching build, fixtures, budgets, faults and invariants. Raw `evaluation.json` includes requested/actual planner, unique invariant fingerprints, actions to first bug, observed graph size, reproduction counts/ratios, original/minimized lengths, model timings/parse failures/fallbacks and memory/thermal snapshots. False-positive adjudication remains explicitly unmeasured; a failed replay is recorded separately. Fallback runs are baselines. No coverage completeness or AI speedup is claimed.

- [Architecture and Mermaid diagrams](Architecture.md)
- [Debug SDK integration](docs/SDK-integration.md)
- [Model artifact/license/setup and physical-device checks](docs/Model-setup.md)
- [Demo script](docs/Demo.md)
- [Dependency pins and official references](docs/Dependencies.md)
- [Implementation plan](docs/Implementation-plan.md)
- [Validation, known limitations and next steps](docs/Validation.md)
