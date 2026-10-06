# Implementation status and validation

Development dates: 2026-10-05–06. Results below distinguish code implementation from exercised behavior.

| Requirement | Implemented | Exercised in this environment |
|---|---|---|
| Pure Kotlin protocol, observations, state normalization and validator | Yes | 20 correctness/contract tests passed |
| Sample, controller and instrumentation APK builds | Yes | Debug APK builds passed; current harness repair validation in progress |
| Checkout duplicate and saved-draft defects, corrected behavior | Yes | Domain assertions passed on JVM; Android checkout reproduced once, full workflow validation in progress |
| Separate sample/runner packages, debug signature bridge | Yes | Actual instrumented exchanges and unauthorized shell denial verified; release bridge exclusion verified |
| Compose accessibility selectors and UI Automator execution | Yes | Actual checkout navigation and rotation exercised; draft/reset orientation repair validation in progress |
| Random and graph exploration, budgets, graph metadata | Yes | JVM protocol discovery and seeded random contract passed; autonomous Android discovery pending |
| Exact replay and distinct outcomes | Yes | JVM outcome/serialization checks passed; one faulty checkout replay reproduced, repeated replay repair pending |
| Dependency-aware chunk minimization and repeat thresholds | Yes | JVM reduction, wrong-predicate and invalid-candidate checks passed; Android reduction pending |
| Exported scenario/Kotlin/evidence/PNG ZIP | Yes | Template/schema checked; actual exported-test compilation/execution pending |
| Local LiteRT-LM Engine adapter and model management | Yes | Pinned API source and AAR verified; physical model inference blocked |
| Dark controller backed by run data | Yes | Android compilation and app launch verified; results review pending |
| Multiple-seed evaluation, raw metrics transfer | Yes | Device evaluation pending; no AI comparison claimed |

The core JUnit suites reported **20 tests, 0 failures, 0 errors, 0 skipped** (17 correctness tests plus 3 exploration contract tests). They cover normalization, validator/schema rejection, scenario round-trip, fixture isolation, fault/corrected domain assertions, idempotent persistence, distinct failure fingerprints, minimization, unavailable/malformed planner fallback, baseline discovery/replay, budgets and seeded determinism. JVM protocol fixtures are not Android execution evidence.

Environment: Windows, JDK 25.0.1, SDK platform 35, Build Tools 36.0.0 and an API 36.1 Google Play x86_64 emulator using WHPX. Android boot, package installs, actual sample/controller launch, Compose selector inspection and screenshot capture succeeded. The unrooted emulator uses a 720×1280 phone display, two cores and 2 GB RAM. No physical device is connected. Early emulator startup attempts failed under host memory pressure; builds and emulator runs are now separated.

Initial Android workflow evidence: the full checkout sequence produced `orderCount = 2` and a `lifecycleResubmission` event. The controlled request-error/clean-reset test passed. The full three-test suite reported two failures: a null value during repeated replay and an offscreen draft selector because a portrait reset left the target in landscape. These are harness validation failures, not corrected-app passes. Provider reacquisition, explicit display rotation and replay stack diagnostics are being validated against the unchanged business assertions.

## Known limitations and next steps

- Physical-device local-model validation is blocked by absence of a physical ARM64 device and a supplied supported model artifact. Follow Model-setup.md; do not count fallback runs as AI-guided or claim memory/thermal/latency benchmarks.
- Current package allowlist supports only the instrumented sample. Arbitrary APKs, inaccessible custom canvas UI and cross-app system workflows require further adapters and supported selectors.
- Faults are acknowledgement gating, controlled request error, rotation and background/reopen. The sample has no feature needing runtime permission denial, so that disturbance is not exposed. No global connectivity manipulation, root, process-death or force-stop equivalence claim.
- No confirmed-crash collector is implemented. Supported business invariant failures are confirmed; deadline breaches are suspected stalls and unexpected runner exceptions are infrastructure failures.
- Run persistence uses atomic JSON/PNG files rather than Room. Very large run histories and large observations require additional storage/performance profiling; the default action budget is bounded.
- Native inference in the pinned runtime requires device checks for cancellation, context capacity, memory and maximum latency. Wall-clock scheduling checks occur between actions; an in-flight native call can overrun that bound.
- The controller reviews after execution. The ADB/test launcher provides instrumentation authorization; an ordinary UI button cannot grant it.
- Minimization reports the smallest reproduction found within its configured search budget, without claiming global minimality. For intermittent defects, choose repetitions and success thresholds explicitly.

An MVP-complete claim requires successful sample/runner builds, actual Android discovery/replay/reduction, compiled and executed export with faulty failure/corrected pass, and real physical-device local inference (or a plainly reported remaining blocked requirement). The pending entries above are not counted as completed validation.
