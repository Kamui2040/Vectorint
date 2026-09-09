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
- desugar_jdk_libs 2.1.5 — Android Open Source Project, GPL-2.0 with the
  Classpath Exception; used to support `java.time` on the minimum Android API.

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

Versions are pinned in the Gradle build. Transitive dependency notices will be
generated and reviewed before any distributable release.
