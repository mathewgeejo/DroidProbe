# Pinned dependencies and API references

| Component | Pin |
|---|---|
| Gradle wrapper | 9.4.0 with distribution SHA-256 |
| Android Gradle Plugin | 9.0.1 |
| Kotlin JVM / serialization / Compose plugins | 2.2.10 (AGP built-in Kotlin for Android) |
| Compile / target SDK | 35 |
| Build tools / minimum Android | 36.0.0 / API 26 |
| Compose UI / Material3 / Activity Compose | 1.7.8 / 1.3.2 / 1.10.1 |
| kotlinx.serialization / coroutines | 1.9.0 / 1.10.2 |
| AndroidX Test runner / JUnit extension / UI Automator | 1.7.0 / 1.3.0 / 2.3.0 |
| JUnit JVM | 4.13.2 |
| AndroidX Core | 1.15.0 |
| LiteRT-LM Android | 0.10.2 |

There are no dynamic dependency selectors. UI Automator uses its stable 2.3.0 API; the new 2.4 DSL is still an alpha API in the documentation checked during implementation. The Android plugin's built-in Kotlin means no `kotlin-android` plugin is applied. API choices were checked against official documentation and the runtime's exact tagged source; successful compilation is tracked separately in Validation.md.

- [AGP 9.0.1 compatibility and built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [AndroidX Test releases](https://developer.android.com/jetpack/androidx/releases/test)
- [Stable UI Automator API](https://developer.android.com/training/testing/other-components/ui-automator-legacy)
- [Compose selectors exposed to UI Automator](https://developer.android.com/develop/ui/compose/testing/interoperability)
- [ContentProvider permission enforcement](https://developer.android.com/reference/android/content/ContentProvider)
- [Signature-level permissions](https://developer.android.com/guide/topics/manifest/permission-element)
- [LiteRT-LM pinned configuration/source](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.10.2/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
- [Gradle JVM compatibility](https://docs.gradle.org/9.4.0/userguide/compatibility.html)
