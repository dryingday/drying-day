# Releasing

How a version of Drying Day is built, signed and published. The design is in
[docs/bolts/06-release.md](bolts/06-release.md). In short: Gradle only builds an **unsigned** APK. The
maintainer signs a copy on their Mac, after checking that it is byte for byte what CI and F-Droid
build. The agent never sees the key or its password.

## The signing key (once)

One key, kept by the maintainer outside the repository: a PKCS #12 keystore, RSA 4096, valid 30 years
(Android wants at least 25, so an update can't outlive the key). The certificate names no one, because it
is public inside every APK.

```
keytool -genkeypair -keystore ~/drying-day-release.p12 -storetype PKCS12 -alias drying-day \
  -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Drying Day"
```

`keytool` asks for the password. Use a long one from a password manager.

**Back the keystore up off the Mac, today.** Losing it means no update can ever reach an installed copy,
from GitHub or from F-Droid. Never put the keystore or its password in the repository, in CI, in an
environment variable or in a chat with the agent.

## Tools on the Mac

- **Build-tools 34.0.0**, for `apksigner`: `sdkmanager "build-tools;34.0.0"`. F-Droid copies the signature
  with `apksigcopier`, and [its page](https://f-droid.org/docs/Reproducible_Builds/) says an `apksigner`
  from build-tools 35.0.0-rc1 on produces APKs that `apksigcopier` can't verify. If newer build-tools turn
  out fine, the script's `apksigcopier compare` step is where that would show.
- **A build-tools of 35 or later**, only for `zipalign -c -P 16` (34.0.0's `zipalign` has no `-P`). The
  script takes `zipalign` from the newest build-tools it finds next to the one it is given.
- **`apksigcopier`**: 1.1.1 on PyPI when this was written (from PyPI's metadata, 2026-11-10). Install it
  into a virtual environment, because macOS's Python refuses `pip install` system-wide (PEP 668):
  `python3 -m venv ~/.venvs/sc && ~/.venvs/sc/bin/pip install apksigcopier`, and put its `bin` on `PATH`.
- `gh`, logged in as the maintainer, and Temurin 21.

None of these is part of the build or CI.

## Cutting a release

1. A release PR from the agent's account: `versionName`, `versionCode` (one more than last time), and
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. High tier, so the maintainer approves it.
2. After it is on `main`, tag that commit and push the tag: `git tag v1.0.0 <commit> && git push origin v1.0.0`.
   CI builds the unsigned APK for the tag from a clean clone and keeps it for 90 days.
3. Run the script, from a clone of the repository:

   ```
   python3 scripts/release.py 1.0.0 --keystore ~/drying-day-release.p12 --alias drying-day
   ```

   It checks the tag, the version and the changelog; builds twice on the Mac, at two paths of different
   lengths; waits for CI's artifact; and stops unless CI's APK, its `.sha256` file and both Mac builds have one
   SHA-256. It then checks alignment, asks for the keystore password (with `getpass`, and passes it to
   `apksigner` on its standard input), signs, verifies with `apksigner` and `apksigcopier compare`, prints the
   certificate's SHA-256 fingerprint for the F-Droid recipe, and creates the GitHub release with
   `drying-day-<version>.apk` and its `.sha256`. Any failure stops it with nothing published.
4. Check the download: `apksigner verify --print-certs drying-day-1.0.0.apk` should show `CN=Drying Day`.
5. The F-Droid recipe in `docs/fdroid/` gets the new commit and version, and the maintainer opens or updates
   the merge request to `fdroiddata` from their own GitLab account.

To try the script without a tag, a key or a password, run `python3 scripts/release.py --dry-run COMMIT` with a
commit on `origin/main`. It signs with a throwaway key, stops before publishing and deletes what it made.

## What the builds ran with

The unsigned APKs from CI and from the Mac were identical for `5586d96` (see the bolt 6 PR 3 description for the
hashes). Versions at that commit:

| | Mac | CI |
|---|---|---|
| JDK | Temurin 21.0.13+9 | Temurin 21.0.13+9 (`actions/setup-java`) |
| Gradle | 9.9.0 | 9.9.0 |
| Android Gradle plugin | 9.5.1 | 9.5.1 |
| SDK platform | android-37.0 rev 2 (`compileSdk` 37) | android-37.0 rev 2, from the image's installed list |
| Build-tools used by AGP | 36.0.0 | 36.0.0 is among the image's (listed below); the log doesn't name the one AGP picked |
| OS | macOS 15 (Darwin 24.6) | `ubuntu-26.04`, image version 20261101.186.1 |

CI's SDK revisions are from the runner image's [included software list](https://github.com/actions/runner-images/blob/ubuntu26/20261101.186/images/ubuntu/Ubuntu2604-Readme.md)
(read 2026-11-10): build-tools 37.0.0, 36.0.0, 36.1.0, 35.0.0, 35.0.1 and 34.0.0, and platform-tools 37.0.1.
The build log doesn't print which build-tools AGP used on CI; the hashes match, so any difference didn't change the bytes.
If a later build differs, compare these first.

## When F-Droid reports a mismatch

The recipe's `Binaries` line means F-Droid publishes only versions whose build matches the signed APK on GitHub.
A release that doesn't reproduce is skipped there until it is fixed. Find out why before changing anything:

1. Build the tag on a fresh clone with `./gradlew --no-build-cache assembleRelease` and compare it with the
   release APK's unsigned content (`apksigcopier` can strip the signature, or compare entry by entry with `unzip`).
2. Known causes, from F-Droid's reproducible builds page: `assets/dexopt/baseline.prof` or `baseline.profm`
   (disable the release ArtProfile tasks), `META-INF/version-control-info.textproto` (`vcsInfo.include = false`
   for `release`), and dex that depends on the CPU count.
3. Each fix changes a build script, `gradle.properties` or the version catalogue, so it is its own high PR,
   opening with the comparison and the line it changes.
