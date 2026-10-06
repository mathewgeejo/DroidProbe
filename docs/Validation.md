# Implementation status and validation

Validation date: 2026-10-06. The Android baseline workflow is exercised end to end. Physical-device local-model inference remains blocked; no AI performance comparison is claimed. [Raw evidence](evidence/README.md) accompanies these results.

The subsequent [controller UI refresh](UI.md) has separate build and visual-review evidence. The workflow results and APK hashes below describe the original captured baseline execution.

| Requirement | Implementation and exercised result |
|---|---|
| Pure Kotlin protocol, observations, normalization and validator | 20 JVM tests passed, zero failures/errors/skips |
| Sample, controller and instrumentation APKs | Debug builds passed; installed APK hashes match the captured artifacts |
| Faulty/corrected checkout, saved draft and reset | All three native `WorkflowTest` tests passed |
| Separate packages and debug signature bridge | Real instrumented exchanges passed; unauthorized shell access denied; release manifest excludes the bridge and permission |
| Compose selectors and UI Automator | Real navigation, text entry, event-synchronized rotation and fresh-reset replays exercised |
| Random/graph exploration and observed graph | Four matching-budget seeded Android runs completed; graph discovered the checkout violation |
| Exact replay and distinct outcomes | Saved autonomous discovery reproduced; faulty and corrected fixtures exercised; JVM tests cover distinct invalid/timeout/infrastructure outcomes |
| Bounded dependency-aware minimization | Android longer fixture reduced 11 to 9 actions after 19 candidate replays |
| Regression ZIP and generated Kotlin execution | Real evidence/PNG ZIP exported; generated class compiled into DEX; corrected invocation passed, faulty invocation failed the intended invariant |
| LiteRT-LM Engine adapter and model management | Pinned API/AAR checked and adapter compiled; physical model inference blocked |
| Native dark controller | Real run history, failure evidence, observed graph, 11-to-9 minimization and actual ZIP export views visually reviewed; unedited captures retained |
| Model-absent baseline path | Separate native fallback run recorded; excluded from AI comparisons |

## Executed tests and regression evidence

The core suites reported **20 tests, 0 failures, 0 errors, 0 skipped**: 17 correctness tests and three exploration contract tests. They cover normalization, schema/action validation, scenario round-trip, fixture isolation, faulty/corrected domain assertions, idempotent persistence, failure fingerprints, dependency-aware minimization, unavailable/malformed planner fallback, discovery/replay, budgets and seeded determinism. JVM fixtures are separate from Android evidence.

`WorkflowTest` reported **OK (3 tests)** in 92.414 seconds. It reproduced checkout duplication, repeated from clean fixtures and verified the unchanged assertion in corrected mode. It also verified faulty/corrected acknowledged-draft recreation and controlled request-error/reset isolation. Provider clients are acquired per exchange; READ/RESET may retry once after provider death. Orientation reset and API-guarded accessibility-cache refresh pair the SDK screen with its actual Compose screen marker before collecting actions.

`DemoTest` reported **OK (1 test)** in 278.509 seconds. Run `demo-1791262455827` contains autonomous graph discovery in **7 actions, 7 observed states and 6 transitions**, followed by a successful exact replay. Separately, an intentionally longer development fixture reduced **11 to 9 actions** after **19 candidate replays**, within its 24-replay/300-second budget. The reduced fixture reproduced in faulty mode and passed the same assertion in corrected mode. All three faulty replays in this demo reproduced; its corrected replay returned `FAILURE_NOT_OBSERVED` with an applicable passing assertion. The autonomous discovery and longer fixture remain explicitly labeled separate sources.

The generated `dev.droidprobe.runner.DroidProbeExportedRegression` was imported with its scenario asset, compiled through AGP's built-in Kotlin source set and checked in the test APK's DEX. Its corrected invocation reported **OK (1 test)** in 48.051 seconds. Its faulty invocation failed after 17.079 seconds with `checkout.at-most-one-order`: expected `orderCount <= 1`, observed `orderCount = 2` for `checkout-1`. This was an assertion failure with replay status `REPRODUCED`, not a classloader, timeout or infrastructure error. The unchanged assertion passed with one order in corrected mode.

The captured ZIP is **5,202,914 bytes**, with SHA-256 recorded in [the artifact manifest](evidence/artifact-manifest.json). It contains the scenario, Kotlin template, harness instructions, JSON evidence and captured PNGs. The verified nine-action scenario and generated class are retained in `runner/src/exportedTest`.

## Seeded baseline evaluation

Both planners used seeds 1 and 2, a 40-action budget, identical installed APKs, faulty mode, initial fixtures, acknowledgement gating and approved invariants. Every invocation reported `OK (1 test)`; replay results are assessed separately from that JUnit completion. APK hashes are recorded in the artifact manifest.

| Planner / seed | Executed actions | Actions to first bug | Unique findings in run | States / transitions | Faulty replay | Corrected assertion |
|---|---:|---:|---:|---:|---|---|
| Random / 1 | 40 | Not observed | 0 | 40 / 39 | Not attempted | Not attempted |
| Random / 2 | 40 | Not observed | 0 | 40 / 39 | Not attempted | Not attempted |
| Graph / 1 | 7 | 7 | 1 | 7 / 6 | 1 / 1 reproduced | Passed |
| Graph / 2 | 7 | 7 | 1 | 7 / 6 | 1 / 1 reproduced | Passed |

These graph findings share one checkout failure fingerprint. Graph scheduling is deterministic; different seed values are repeat runs for this planner. Random runs exhausted their budgets without a finding. This small sample establishes behavior in the configured sample app, not general exploration superiority, coverage completeness or an AI speedup. A larger observed-state count does not imply more confirmed defects. Explorer elapsed times are distinct from full instrumentation duration, which also includes replay, minimization and export.

The graph discovery sequences remained seven actions under the separate 12-replay minimization budget, which was exhausted; this does not prove minimality. The successful 11-to-9 reduction above belongs to the separately labeled longer development fixture. False-positive adjudication is unmeasured (`null` in evaluation output); failed reproduction is counted separately. No absent-model fallback contributes to an AI-guided cohort.

The separate local selection (seed 1, budget 40) reported `OK (1 test)` and explicitly identified itself as `graph-baseline (local model unavailable or no accepted proposals)`, with **zero inference calls, zero accepted model proposals and six fallbacks**. It found the checkout violation in seven actions, reproduced it and passed corrected replay. This validates the missing-model behavior, not native AI inference. [Evaluation rows](evidence/evaluation.json) retain requested/actual planners, timings, raw reproduction counts, model statistics and device memory/thermal snapshots; corresponding full run JSON and native logs are beside them.

## Environment and practical limits

Windows host, JDK 25.0.1, SDK platform 35, Build Tools 36.0.0 and an unrooted API 36.1 Google Play x86_64 emulator under WHPX. The owned emulator uses a 720 × 1280 display, two cores, 2 GB RAM, SwiftShader, disabled animations and an awake screen. Its Play Store, Google Play services and Google Partner Setup packages were disabled to reduce unrelated background load on the 8 GB host. These are disclosed test-environment settings, not DroidProbe fault capabilities. No physical device was connected.

Early harness failures exposed provider lifetime, orientation reset and accessibility readiness issues; the final suite above passed after their repair. Pausing/resuming the emulator VM also left native input surfaces unusable; rebooting restored input. The reported final cohort ran on the live emulator without VM pause. Baseline runs need no model or root, and the apps declare no Internet permission.

- Physical ARM64 inference is blocked by absence of a connected device and a supported `.litertlm` artifact. Follow [Model-setup.md](Model-setup.md). Compiled bindings and fallback tests do not establish native inference correctness, memory, thermal or latency behavior.
- The package allowlist supports the instrumented sample. Arbitrary APKs, inaccessible custom canvas UI and cross-app system workflows need further adapters and supported selectors.
- Supported disturbances include acknowledgement gating, controlled request error, rotation and background/reopen. There is no runtime-permission feature in the sample. No global connectivity manipulation, root or process-death equivalence is claimed; fixture reset uses force-stop only to establish isolation.
- No confirmed-crash collector is implemented. Supported business invariant failures are confirmed; workflow deadlines are suspected stalls and unexpected runner exceptions are infrastructure failures.
- Persistence uses atomic JSON/PNG files. Larger histories need storage and performance profiling. The controller reviews results after authorized ADB instrumentation; it cannot grant itself instrumentation privileges.
- Native inference cancellation and strict latency bounds need physical validation. Scheduling checks run between actions; an in-flight native call can overrun the wall-clock bound.
- Minimization returns the smallest reproduction found within its budget, not a global minimum. Emulator reduction used one reproduction per candidate; intermittent defects require explicit repetitions and thresholds.

The baseline discovery/replay/reduction/export workflow is validated. Completing the AI-guided validation requires a physical-device model run with accepted proposals and equal-budget baseline comparisons, retaining both successful and worse outcomes.
