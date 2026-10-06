# Captured validation evidence

These files were copied from executed JVM/instrumentation results and device reports on 2026-10-06. They are evidence artifacts, not seeded observations for the explorer. See [Validation.md](../Validation.md) for the environment and limits.

- `core-tests.xml`, `exploration-contract-tests.xml`: 20 passing JVM tests.
- `workflow.log`: three passing native workflow tests.
- `demo.log`, `demo-run.json`: autonomous discovery, exact replay, separately labeled longer-fixture reduction and faulty/corrected validation.
- `exported-corrected.log`, `exported-faulty.log`: the compiled exported class passes corrected mode and fails faulty mode on the checkout business invariant.
- `artifact-manifest.json`: SHA-256 hashes of the final APK artifacts, checked against installed APK hashes, and the captured regression ZIP. It identifies which executions used these APKs.
- `evaluation.json`, `explore-*.json`, `random-*.log`, `graph-*.log`: four equal-budget baseline runs and their actual reports. The evaluation file also includes the separately labeled missing-model run.
- `model-absent.log`: native completion of that fallback run, with no model inference claimed.
- `controller-*.png`: unedited emulator captures of the native controller reviewing stored results.

The regression ZIP lives at `.local/device-results/demo-1791262455827/regression.zip` in this workspace. It contains captured PNGs and JSON evidence. Its reduced scenario and generated Kotlin class are also retained in `runner/src/exportedTest` so the regression can be compiled without rerunning exploration. Raw report screenshot names refer to files inside their original run directories or bundle; they are not synthetic images.

Native model inference remains unverified. A missing-model run demonstrates explicit graph fallback and must not be included as an AI-guided comparison.
