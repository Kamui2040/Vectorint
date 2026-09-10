# F-Droid build and signing

Vectorint is designed for a standard F-Droid source build.

## Distribution model

F-Droid builds an exact public release tag and signs the resulting APK with its own
repository-specific key. Vectorint's permanent developer signing identity is used
for GitHub and other compatible storefronts instead.

The F-Droid metadata therefore does not use `Binaries`, a per-build `binary`,
`AllowedAPKSigningKeys`, or developer-signature copying. This avoids coupling the
F-Droid release to the ZIP layout and signature reconstruction of an APK built on a
different host.

Because no developer signature is copied, the F-Droid APK does not need to be
byte-identical to an APK built on the maintainer's host. The F-Droid-native build
must still succeed from the exact public source, produce the expected package and
version, pass APK checks, and contain no unexplained app-payload differences.

The package name remains `io.github.kamui2040.vectorint`, but Android will not
update an F-Droid-signed install with a developer-signed APK, or the reverse,
without a supported signing migration. Users must uninstall before changing
channels.

## Build baseline

- OpenJDK 21
- Android SDK Platform 37.0
- Android SDK Build-Tools 37.0.0
- Gradle 9.7.1 from the checked-in wrapper
- Android Gradle Plugin 9.4.0
- release task: `assembleRelease`
- output: `app/build/outputs/apk/release/app-release-unsigned.apk`

Release builds exclude AGP-generated VCS metadata so identical tracked source does
not change merely because a `.git` directory is present.

## Release gate

Before an F-Droid contribution:

1. Select the exact public release version, version code, tag, and source commit.
2. Build from a pristine checkout of that commit with the pinned toolchain.
3. Build the same tracked source exported without `.git` and require the complete
   unsigned APK bytes to match.
4. Run the metadata recipe in the current F-Droid buildserver environment and
   verify the expected APK artifact, not only a successful command or pipeline.
   Commit proposed metadata in the validation checkout first and verify that
   `git log -n1 --pretty=%ct -- metadata/io.github.kamui2040.vectorint.yml`
   returns a non-empty timestamp. This matches the real `fdroiddata` path and
   prevents an empty `SOURCE_DATE_EPOCH` from corrupting Gradle's cache journal.
5. Run `fdroid readmeta`, `fdroid rewritemeta`, `fdroid checkupdates`, `fdroid lint`,
   and the local source build required by the current contribution guide.
6. Confirm the repository, metadata, screenshots, licences, and build inputs are
   public-safe before opening the inclusion request.

Do not add post-build ZIP rewriting merely to make two artifacts appear similar.
Fix the nondeterministic build input or environment instead.

## Metadata reference

The contributor reference is
`fdroid/io.github.kamui2040.vectorint.yml.template`. The exact release build entry
belongs in the eventual `fdroiddata` contribution after the production version and
tag exist. Once submitted, production metadata in `fdroid/fdroiddata` is the
authoritative F-Droid record.
