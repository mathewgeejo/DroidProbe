# Debug SDK integration

The supported integration is an instrumented sample app with deliberate state signals and semantic selectors. Supporting a new application requires an explicit adapter and package allowlist change; arbitrary APK exploration is not currently implemented.

1. Depend on `testing-sdk` only through `debugImplementation`. The pure `core` schema can be shared separately if needed.
2. Put the provider class and manifest declaration in `src/debug`. Never merge the bridge into release manifests.
3. Declare a signature-level test permission and require it on the exported provider. Sign the runner and target with the same development key; different developers' default debug keys do not match.
4. Extend `DebugProbeProvider`, implement the typed exchange, check protocol/run IDs, and serialize state mutation on the application owner thread. The sample waits for the main thread and commits one immutable state snapshot.
5. Expose logical operation IDs, acknowledgement/persistence events, lifecycle generations, saved content, fault state and reset. Keep events scoped to one fixture run. Do not infer order persistence from a spinner or screenshot.
6. Expose Compose `Modifier.testTag` keys under `testTagsAsResourceId = true`. Use `By.res(key).pkg(package)`; the two-argument resource-package selector does not match bare Compose tags. The sample also exposes `screen_<lowercase screen name>` on its heading, so observation waits for the UI tree to match the SDK screen after relaunch/navigation.

Relevant Android behavior is documented in the [ContentProvider call API](https://developer.android.com/reference/android/content/ContentProvider#call(java.lang.String,%20java.lang.String,%20android.os.Bundle)), [signature permission declaration](https://developer.android.com/guide/topics/manifest/permission-element), and [Compose/UI Automator interoperability](https://developer.android.com/develop/ui/compose/testing/interoperability). `call` does not enforce per-method read/write access for you; DroidProbe explicitly checks every incoming exchange and the caller signature before dispatching it.

The bridge supports READ, RESET, CONFIGURE_FAULT and RELEASE_FAULT. READ returns typed state, operation/lifecycle events and the inputs to approved invariant checks. Executable assertions run in `core`, shared by exploration, replay and export. RESET overwrites order/draft/backend/events/counters atomically and selects faulty/corrected mode and starting fixtures. It has no arbitrary reflection, file access or shell command endpoint.

The runner declares package/provider visibility and the sample permission. Test instrumentation targets the runner app, so resolver calls use its signed UID; the target app remains a different package/process. Unauthorized shell content calls should fail with SecurityException. Verify both the successful instrumented exchange and denial on each new supported device. Inspect the merged **release** manifest before distributing a target app.

`ProbeClient` acquires and closes an unstable provider client for each exchange, so a target restart does not retain a dead provider handle. A single dead-provider retry is allowed for idempotent READ/RESET commands; other mutations surface an infrastructure error. Replay retains the exception stack and failing stage for diagnosis.
