# Implementation plan

1. **P0 vertical slice**: typed ActionIR and observations, atomic resettable checkout/draft engine, debug signature permission bridge, separate Compose sample, self-instrumenting runner, event-synchronized rotation and exact replay. Validate on the installed emulator.
2. **P1**: observed-state graph, generic graph/random action scheduling, invariant evidence, bounded dependency-aware delta debugging, real replay outcomes, export from validated ActionIR.
3. **P2**: pinned official LiteRT-LM adapter, model status/hash/configuration, bounded proposal repair and graph fallback, honest inference labels. Physical-device model validation remains explicit if no device/artifact is available.
4. **P3**: controller configuration and review backed by persisted run JSON, graph/failure/minimization views, shareable export, architecture/integration/model/demo documentation.

Automation is launched by authorized `adb shell am instrument`, using AndroidJUnitRunner and UI Automator. The controller reviews files and stores configuration; it cannot grant itself instrumentation privileges. The sample and runner use the same development signing key. There is no root dependency or cloud inference.

Builds are milestone checks requested by the implementation task, not automatic checks after every edit. Preserve unrelated files. No physical device was connected at initial inspection; the existing API 36.1 x86_64 emulator image supports WHPX.
