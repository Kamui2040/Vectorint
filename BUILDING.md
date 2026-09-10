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

Signing keys and credentials are not part of the repository. GitHub and compatible
storefront releases are signed outside the source tree with Vectorint's permanent
developer identity.

F-Droid is a separate update channel. It builds the exact public release source and
signs that build with its own repository-specific key. The F-Droid recipe must not
use `Binaries`, a per-build `binary`, or developer-signature copying under this
policy. Because Android treats the F-Droid and developer certificates as different
app identities for updates, moving between those channels requires uninstalling the
existing app first.

Before the first release, require a byte-for-byte match between a pristine Git
checkout and the same tracked source without `.git` when both use the pinned local
toolchain. Separately build and inspect the APK in the current F-Droid environment.
Cross-host ZIP compression bytes do not need to match under the separate-signature
policy, but app payload differences must be explained before release. A successful
local build alone is not a release-readiness result. See [FDROID.md](FDROID.md) for
the complete F-Droid gate.
