# Dependency and provenance inventory

Vectorint contains no imported application source, assets, or translations. The
initial repository uses the following public build, runtime, and test components.

## Runtime

- AndroidX Activity Compose 1.13.0 — Android Open Source Project, Apache-2.0.
- AndroidX AppCompat 1.8.0 — Android Open Source Project, Apache-2.0.
- Jetpack Compose libraries selected by BOM 2026.08.00 — Android Open Source
  Project, Apache-2.0.
- AndroidX Preferences DataStore 1.2.1 — Android Open Source Project, Apache-2.0.
- AndroidX Room runtime 2.8.4 — Android Open Source Project, Apache-2.0.
- Kotlin Coroutines Android 1.11.0 — JetBrains and contributors, Apache-2.0.
- Kotlin Serialization 1.7.3 — JetBrains and contributors, Apache-2.0.
- Okio 3.9.1 — Square and contributors, Apache-2.0.
- JetBrains Annotations 23.0.0, JSpecify 1.0.0, and Guava ListenableFuture
  1.0 — supporting transitive libraries, Apache-2.0.
- desugar_jdk_libs 2.1.5 — Android Open Source Project, GPL-2.0 with the
  Classpath Exception; used to support `java.time` on the minimum Android API.

## Google Play edition only

- Google Play services ML Kit document scanner 16.0.0 — Google, governed by the
  Google APIs Terms of Service; used only by `playDebug` and `playRelease` for
  on-device receipt capture and cleanup.
- Google Play services ML Kit text recognition 19.0.1 — Google, governed by the
  Google APIs Terms of Service; used only by `playDebug` and `playRelease` to
  read receipt text on-device.

These proprietary optional components are absent from the standard `debug` and
`release` variants, including the F-Droid build. Their documentation and terms
are available at <https://developers.google.com/ml-kit/vision/doc-scanner> and
<https://developers.google.com/ml-kit/terms>.

## Build and test only

- Android Gradle Plugin 9.4.0 — Android Open Source Project, Apache-2.0.
- AndroidX Room Gradle plugin and compiler 2.8.4 — Android Open Source Project,
  Apache-2.0.
- Kotlin Symbol Processing plugin 2.3.11 — Google, Apache-2.0.
- Kotlin Compose compiler Gradle plugin 2.3.21 — JetBrains and contributors,
  Apache-2.0.
- Gradle Wrapper 9.7.1 — Gradle Build Tool project, Apache-2.0.
- ktlint Gradle plugin 14.2.0 — its contributors, MIT.
- JUnit 4.13.2 — JUnit contributors, EPL-1.0.
- Robolectric 4.16.1 — Robolectric contributors, MIT.
- Jetpack Compose UI test libraries selected by BOM 2026.08.00 — Android Open
  Source Project, Apache-2.0.

Versions are pinned in the Gradle build. The complete standard release runtime
dependency graph was resolved and reviewed for the first production release; it
contains no unexpected or non-free dependency families.
