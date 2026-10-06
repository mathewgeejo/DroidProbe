# Local model setup and physical-device validation

The adapter is implemented against **LiteRT-LM Android 0.10.2**, pinned in `model-runtime`. Its Maven POM uses Kotlin 2.2.21 metadata and includes ARM64 and x86_64 JNI libraries; the project's Kotlin 2.2 toolchain can consume that metadata. The 0.17.1 artifact checked during implementation requires Kotlin 2.4.0, so this MVP deliberately selects the earlier compatible release. Check the pinned source when changing runtime versions:

- [Official Android setup](https://developers.google.com/edge/litert-lm/android)
- [0.10.2 Engine configuration](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.10.2/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
- [0.10.2 Conversation API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.10.2/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
- [Published 0.10.2 POM](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.10.2/litertlm-android-0.10.2.pom)

Use an actual **`.litertlm`** artifact, not a `.task`, GGUF or raw TFLite file renamed with another extension. The [LiteRT Community Gemma 3 1B IT model card](https://huggingface.co/litert-community/Gemma3-1B-IT) links a small dynamic INT4 QAT LiteRT-LM artifact. It is a candidate for initial benchmarking, not a device compatibility guarantee. Review the model's Gemma license and access terms at its source. The runtime itself is Apache 2.0. No model weights are bundled or downloaded automatically, and no credentials are required by DroidProbe.

After downloading the artifact from its publisher and recording its revision and license, use the model installation script:

```powershell
./scripts/Install-Model.ps1 -ModelPath C:/models/gemma3-1b-it-int4.litertlm
```

It installs `files/models/model.litertlm` and `model.sha256` in the debuggable runner. The adapter hashes the complete file before initialization and reports missing/checksum/incompatible-runtime states. It currently requires the ARM64 device validation path, even though the AAR contains an x86_64 library. Baseline execution works without model weights.

Configuration: CPU with two threads, token context 2048, fresh conversation per decision, topK 1, topP 0.9, temperature 0.1 and seed 1. No GPU or NPU capability is assumed. The runtime exposes a context bound rather than a separate output-token cap in this pinned API. Native inference cancellation/latency under a strict wall-clock deadline still requires physical validation. Loading, checksum and inference run off the main thread. Pending checkout acknowledgements use graph actions without inference to avoid interference in the fault window.

Run a small budget first:

```powershell
adb shell am instrument -w -e class dev.droidprobe.runner.ExplorationTest -e planner local -e budget 12 -e seed 1 dev.droidprobe.runner.test/androidx.test.runner.AndroidJUnitRunner
```

Check `run.json` for initialized model identity, nonzero `inferenceCount` and `modelAccepted`, plus actual `plannerIdentity`. A local selection with no accepted proposals is labeled a graph baseline. Record `initializationMs`, inference duration, parse failures/fallbacks, device memory/PSS and thermal status. Verify that the process stays responsive and does not OOM. Repeat with a larger budget only after a successful small-device benchmark.

The planner receives current compact observations, available actions, recent action history, goal, approved invariants and remaining budget. It selects an enumerated option with a short rationale. JSON/schema checks and one repair attempt precede graph fallback. It does not receive the reproduction fixture, seeded-defect names or arbitrary executable commands. Fresh conversations keep prompt history bounded.

Then run equal-budget random, graph and local seeds with `scripts/Evaluate.ps1`. Preserve slower/worse AI outcomes. Report any local-to-baseline fallback, both raw counts and ratios. Native model inference on a physical device has **not yet been exercised in this development environment**. There was no connected physical device and no authorized model artifact at initial inspection. These remain prerequisites before claiming the complete AI-guided MVP.
