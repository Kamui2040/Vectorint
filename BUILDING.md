# Building Vectorint

## Requirements

- OpenJDK 21
- Android SDK Platform 37.0
- Android SDK Build-Tools 37.0.0

The Gradle wrapper version and download checksum are pinned in the repository.
All other direct build and runtime versions are listed in
[DEPENDENCIES.md](DEPENDENCIES.md).

## Development checks

```shell
./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug
```

## Release build

```shell
./gradlew clean assembleRelease
```

This creates `app/build/outputs/apk/release/app-release-unsigned.apk`.

Signing keys and credentials are not part of the repository. A production release
must be signed outside the source tree with Vectorint's permanent signing identity.
Before the first release, the exact signed APK must pass the independent F-Droid
reproducibility and signature-copying checks. A successful local build alone is not
a release-readiness result.
