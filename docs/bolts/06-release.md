# Bolt 6: release

The app ships. It gets an icon, a release build, a store listing, a privacy policy and a signing key, and `v1.0.0` is published on GitHub as a signed APK that F-Droid can rebuild byte for byte and ship under the same signature.

After this bolt the app is on GitHub Releases, and a merge request to F-Droid's `fdroiddata` is ready for the maintainer to open.

## In scope

### The icon

An adaptive icon, vector only. `minSdk` is 26, so every device the app runs on draws adaptive icons, and no PNG goes in the APK.

- `androidApp/src/main/res/mipmap-anydpi/ic_launcher.xml`, an `<adaptive-icon>` with three layers, each a vector drawable in `res/drawable/` on the 108 dp canvas, with everything that matters inside the 66 dp safe circle:
  - **background**, `ic_launcher_background.xml`: one flat, mid-to-deep sky blue, dark enough for the contrast rule below;
  - **foreground**, `ic_launcher_foreground.xml`: a washing line across the icon with one white T-shirt on it, held by two pegs, and a yellow sun behind its upper right;
  - **monochrome**, `ic_launcher_monochrome.xml`, for Android 13's themed icons: the line, the T-shirt and the pegs, as one shape.
- Contrast between the white T-shirt and the background of at least 3:1, recorded in the PR. A pale sky blue doesn't reach it (about 1.7:1), so the blue is chosen to.
- The one manifest change in this bolt: `android:icon="@mipmap/ic_launcher"` on `<application>`. No `roundIcon`: an adaptive icon is masked by the launcher.
- The drawing's source is one SVG, `art/icon.svg`, from which the three vectors are converted by hand and `fastlane/metadata/android/en-US/images/icon.png` (512 × 512) is rendered. The PR says which tool rendered it.

The sketch above is the brief. The PR shows the icon on the emulator's home screen, in light and dark, and themed; the maintainer can ask for changes there.

### The release build

`./gradlew assembleRelease` builds `androidApp-release-unsigned.apk`. Gradle never signs a release: there's no `signingConfig` for it, no `keystore.properties` and no signing secret anywhere in the repository or CI. Signing happens after the build, outside Gradle (below), so the APK F-Droid builds and the one the maintainer signs come out of the same command on a clean clone: `./gradlew --no-build-cache assembleRelease`, with nothing else on the command line.

In `androidApp/build.gradle.kts`, the `release` build type:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

- **R8 on.** The debug APK measured 11.1 MB on 2026-11-10, most of it library code the app doesn't call. The libraries in the catalogue ship their own consumer rules (kotlinx.serialization, Ktor, Compose), so `proguard-rules.pro` starts empty. Every rule later added to it is listed in the PR with the R8 message or the crash that asked for it. No blanket `-keep class **`, no `-dontobfuscate` unless the reproducibility check needs it, and no `-dontwarn` without the missing class named. Ktor is known to make R8 report missing `org.slf4j` classes on Android, so a `-dontwarn` for exactly the classes R8 names is expected there. No `-keep` rule is added without the failing test or emulator step that needs it.
- **Nothing else changes** in the build: `dependenciesInfo` stays off, `vcsInfo` stays at AGP's default (the textproto it adds names the commit, and every release is built from the tagged commit's clean checkout, so it matches), no ABI splits, no app bundle.
- `lintVitalRelease`, which `assembleRelease` runs, must pass.
- If the release build resolves artifacts `gradle/verification-metadata.xml` doesn't have yet (R8, lint's release configurations), the entries come from `scripts/update-verification-metadata.py` and are listed in the PR. This spec names no coordinates for them, so it doesn't cover them: PR 2 is high anyway, and its description asks the maintainer about them.
- The release APK is the one the emulator run, the screenshots and the reproducibility check use. A minified build can fail where the debug one works, so the emulator run exercises every path that decodes JSON: a forecast fetch, a place search, the forecast cache and the place store read back after the app is killed.

### Version

`versionName = "1.0.0"`, `versionCode = 1`. Nothing has been released under code 1, so it stays. Each later release adds one to the code. The tag is `v` and the version name: `v1.0.0`.

### Reproducibility

The aim, from `docs/PLAN.md`: anyone, F-Droid included, can build the unsigned APK from the tagged commit and get the same bytes as the APK the maintainer signed, apart from the signature.

What's compared: the unsigned release APK, each built with `./gradlew --no-build-cache assembleRelease` on a fresh clone of the same commit, by

1. CI on Ubuntu (Temurin 21), in the step described under CI, and
2. the Mac (Temurin 21), twice, from clones at two different paths.

All three must have the same SHA-256. This is checked twice:

- **In PR 3**, on a commit on `main`, as a rehearsal. The PR records the hashes.
- **At the release**, on the tagged commit: the release script (below) builds twice on the Mac, at two paths, and compares both with CI's build for the tag before it signs or publishes anything. This is the check that counts, because it's the commit that ships. PR 6 records the script's output.

`docs/releasing.md` records what the builds ran with: JDK build, Gradle, AGP, and the SDK platform and build-tools revisions on the Mac and on CI's runner image.

If the hashes differ, the PR lists the APK entries that differ (from `unzip -l` and a comparison of each entry's bytes). Known causes, from [F-Droid's reproducible builds page](https://f-droid.org/docs/Reproducible_Builds/) (read 2026-11-10): `assets/dexopt/baseline.prof` or `baseline.profm`, fixed by disabling the release ArtProfile tasks; `META-INF/version-control-info.textproto`, fixed by `vcsInfo.include = false` for `release`; dex that depends on the CPU count. This spec covers none of the fixes: each changes a build script, `gradle.properties` or the catalogue, so each is its own high PR, opening with the comparison and the line it changes.

F-Droid's own build of the recipe (below), in the `fdroiddata` merge request's pipeline, is the final check. It runs on Debian, possibly with a different JDK build, which this bolt can't fully reproduce beforehand.

### CI

The existing job, "Check and assemble", also runs `assembleRelease`: `./gradlew --continue check assembleDebug assembleRelease`, so a change that breaks the minified build fails its PR. Its name, and so the ruleset's required check, doesn't change.

- `on.push` gains `tags: ['v*']` beside `branches: [main]`.
- Only when `github.event_name == 'push'`, two more steps after the check:
  - clone the checked-out commit into `$RUNNER_TEMP/release` (`git clone` of the workspace, then `git checkout` of `github.sha`) and run `./gradlew --no-build-cache assembleRelease` there: the same command F-Droid and the release script use, so neither the build in the workspace nor the Gradle cache can stand in for a clean one;
  - upload that `androidApp-release-unsigned.apk` and `androidApp-release-unsigned.apk.sha256` (`sha256sum`'s format, `<hash>  <file name>`) as the artifact `release-unsigned-<github.sha>`, kept for 90 days, with `if-no-files-found: error`.
- `concurrency` is unchanged, so push runs aren't cancelled.
- No secrets, no signing, no release publishing from CI. `permissions: contents: read` stays.
- Actions pinned to full commit SHAs, as now. The upload uses the `actions/upload-artifact` already pinned.

### Signing

- **One key, kept by the maintainer** on their Mac, outside the repository: a PKCS #12 keystore, RSA 4096, valid 30 years (Android asks for at least 25 so updates can't outlive the key), created with `keytool`. The command is in `docs/releasing.md`.
- **The certificate names no one.** It's public, inside every APK, so its subject is `CN=Drying Day` and nothing else: no name, no organisation, no place.
- **A backup** off the Mac, which the maintainer keeps. Losing the key means no update can ever reach an installed copy, from GitHub or F-Droid.
- **The agent never reads the key or its password.** The maintainer runs the signing step. The release script reads the password with Python's `getpass` from the terminal, never from a file, an argument or the environment.
- Signed with `apksigner sign --ks <keystore> --ks-key-alias <alias> --min-sdk-version 26 --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --out <signed> <unsigned>` (v1 isn't needed from API 24). AGP already aligns the unsigned APK; the script checks that with `zipalign -c -P 16 -v 4` before signing and stops if it isn't. F-Droid copies the signature with `apksigcopier`, and its page says `apksigner` from build-tools 35.0.0-rc1 on produces APKs that `apksigcopier` can't verify, so the script uses build-tools 34.0.0's `apksigner`, installed with the SDK manager. The script verifies the result with `apksigcopier compare` against the unsigned APK, so if newer build-tools turn out fine, that check shows it. `apksigcopier` is installed on the Mac with `pip`, its version from PyPI's metadata recorded in `docs/releasing.md`. Neither tool goes into the build or CI.

### The release script

```
python3 scripts/release.py 1.0.0 --keystore PATH --alias NAME [--build-tools DIR]
python3 scripts/release.py --dry-run COMMIT [--build-tools DIR]
```

Run by the maintainer on the Mac once the release PR is on `main` and the tag is pushed. `--build-tools` defaults to `$ANDROID_HOME/build-tools/34.0.0`. It:

1. checks that `v<version>` exists on `origin`, that `versionName` at that commit is `<version>`, and that `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` exists there;
2. clones the repository at the tag into two new temporary directories with different path lengths and runs `./gradlew --no-build-cache assembleRelease` in each, one after the other;
3. downloads CI's artifact `release-unsigned-<commit>` with `gh run download`, from the `push` run for the tag, waiting up to 30 minutes for it to finish, and stops unless CI's APK, the `.sha256` file beside it and both local builds all have one SHA-256;
4. checks alignment, asks for the keystore password and signs a copy, as under Signing, then runs `apksigner verify --print-certs` and `apksigcopier compare` against the unsigned APK;
5. prints the certificate's SHA-256 fingerprint, for the F-Droid recipe;
6. creates the GitHub release `v<version>` with `drying-day-<version>.apk` and `drying-day-<version>.apk.sha256` (`sha256sum`'s format), and the changelog as the notes. It uses `gh` as whoever is logged in, which is the maintainer.

Every step stops the script on failure, with nothing published.

`--dry-run COMMIT` tests the script without a tag, a real key or a password: it takes any commit on `origin/main`, skips the checks in step 1 that need a tag, makes a throwaway keystore with a random password in a temporary directory (passed to `apksigner` with `--ks-pass pass:…`, the only time a password is on a command line), runs steps 2 to 5, and stops before step 6. The temporary directory is deleted at the end. The agent can run it.

The parts that don't call a tool (reading `versionName` and `versionCode`, the file names, the changelog path, the `.sha256` format) are functions tested in `scripts/`. A test also shows that a real run reads the password through `getpass` and passes it to `apksigner` on its standard input, never as an argument, since the dry run doesn't take that path.

### The store listing

In `fastlane/metadata/android/en-US/`, the layout F-Droid reads ([its page](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/), read 2026-11-10):

- `title.txt`: Drying Day
- `short_description.txt` (80 characters at most): "Will the washing dry outside today? A clear verdict and when to bring it in."
- `full_description.txt` (4000 at most): what the app does (the three verdicts, light and heavy loads, the times, tips and the indoor plan, three days), that it works offline from the last forecast, what leaves the device (the same points as the privacy policy, in short), no account, no ads, no tracking, the Open-Meteo and GeoNames credits, and a link to the privacy policy.
- `changelogs/1.txt`: "First release."
- `images/icon.png`: from the icon above.
- `images/phoneScreenshots/1.png` to `4.png`: a will-dry day in light, a partly-dry day in dark, a don't-bother day showing the indoor plan, and first run. From the release build on the emulator (API 36, 1080 × 2400), the status bar in demo mode. The place is a device fix, so the screen says "Approximate location" and names nowhere; the forecasts come from fixtures through a fake source in a local patch that isn't committed, as bolt 5 did.

No feature graphic and no translations in v1.

### Privacy policy

`PRIVACY.md` at the repository root, linked from the README and the full description. Plain words, and nothing it says beyond what the specs for bolts 3 to 5 already set:

- No account, no analytics, no ads, no crash reporting. The app's developer receives nothing.
- What leaves the device, and to whom: the forecast request to `api.open-meteo.com` (coordinates rounded to two decimals, about a kilometre), and a place search's text and the device's language to `geocoding-api.open-meteo.com`. Both over HTTPS. Open-Meteo sees the device's IP address, as any server does, under [its own terms and privacy policy](https://open-meteo.com/en/terms). The app sends no identifier, and its user agent is Ktor's generic one, not Android's, which names the device model. Whatever the phone's HTTP stack adds itself is outside the app's control.
- When: a search only when the user submits it; the forecast as bolt 5 sets out, at most about once an hour while the app is open, and never in the background.
- Location: approximate only, once, when the user taps the button. The phone's own location service, which may be run by its maker or Google, is outside the app.
- What's kept on the device: saved places and their last forecasts, and the load choice. On Android, excluded from backup and device transfer. Uninstalling removes it.
- Where the app is installed from (F-Droid, GitHub) may have its own policy.
- A contact: the repository's issue tracker.
- Dated, and changed only by PR.

### The F-Droid recipe

A draft of the `fdroiddata` metadata, `docs/fdroid/io.github.dryingday.yml`, for the maintainer to submit from their own GitLab account. From [the build metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/) and `fdroiddata`'s `config/categories.yml` (both read 2026-11-10):

```yaml
Categories:
  - Weather
License: GPL-3.0-or-later
SourceCode: https://github.com/dryingday/drying-day
IssueTracker: https://github.com/dryingday/drying-day/issues
Changelog: https://github.com/dryingday/drying-day/releases

AutoName: Drying Day

RepoType: git
Repo: https://github.com/dryingday/drying-day.git
Binaries: https://github.com/dryingday/drying-day/releases/download/v%v/drying-day-%v.apk

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: <full SHA of v1.0.0>
    subdir: androidApp
    output: build/outputs/apk/release/androidApp-release-unsigned.apk
    gradle:
      - yes

AllowedAPKSigningKeys: <the certificate's SHA-256, lowercase hex>

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

The build server will likely need more than this: `sudo:` lines installing JDK 21, and the SDK platform and build-tools the build uses. Those are added following current `fdroiddata` recipes for AGP 9 apps, and the PR says which it copied. The YAML isn't the evidence that the recipe works: the `fdroiddata` merge request's pipeline is, or a local `fdroid build` if the maintainer runs one. No anti-features are claimed: there's no non-free dependency, and Open-Meteo's server is open source (AGPL-3.0, per its GitHub repository on 2026-11-10). The hosted free API is for non-commercial use; if F-Droid's reviewers ask for `NonFreeNet` on that account, that's the maintainer's call in the merge request. `docs/releasing.md` gets a line saying `Binaries` means F-Droid publishes only versions that reproduce: a release that doesn't is skipped there until fixed.

### Docs

- `docs/releasing.md`: making the key (once), the backup, cutting a release (PR, tag, `scripts/release.py`), the tool versions, and what to do when F-Droid reports a mismatch.
- `README.md`: the status line becomes "Version 1.0.0", with links to the GitHub release and, once it's listed, F-Droid.

### Network, permissions, dependencies

None new. The app makes the same requests as after bolt 5. No permission changes; the manifest gains only `android:icon`. No catalogue change; no verification metadata change expected (see the release build). `apksigner` (build-tools 34.0.0) and `apksigcopier` are tools on the maintainer's Mac, not dependencies of the build.

## Out of scope

Opening the `fdroiddata` merge request (the maintainer's, from their account). Google Play, app bundles, iOS. Translations of the app or the listing. A feature graphic. An in-app about or licences screen. Notifications, widgets. Any change to what the app does.

## Acceptance criteria

- `./gradlew check assembleDebug assembleRelease` passes in CI and locally, and the tests need no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- The merged manifest of the release APK differs from bolt 5's only in `android:icon`, shown by a diff in the PR.
- The release APK on the emulator (API 36): first run, a device fix, a search, the main screen with a live forecast, the app killed and reopened offline showing the cached forecast and the place, the icon in light, dark and themed. Screenshots in the PR.
- Three unsigned release builds of one commit (CI, and two Mac clones at different paths) with one SHA-256: recorded in PR 3 for a commit on `main`, and for `v1.0.0` in the release script's output and PR 6.
- `scripts/release.py` tests pass, and `--dry-run` on a commit on `main` builds, matches CI's hash, signs, verifies with `apksigner` and `apksigcopier`, and stops before publishing, leaving nothing behind.
- `fastlane/` lengths within F-Droid's limits, checked by a test in `scripts/` that checks every file that exists, so it passes before the listing is added and covers each file as it arrives.
- No real places or personal data in the listing, screenshots, privacy policy or certificate.
- `v1.0.0` on GitHub Releases with the signed APK and its SHA-256, and `apksigner verify --print-certs` on the downloaded APK showing `CN=Drying Day`.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (signing, who ships the APK, the icon) |
| 1 | The icon: SVG source, three vector layers, `android:icon`, the 512 px PNG | medium (the manifest attribute named here) |
| 2 | The release build type and `proguard-rules.pro`, `assembleRelease` and the artifact upload in CI, the release-build emulator run | high (build script, CI) |
| 3 | `scripts/release.py` with tests, the `fastlane/` length test, `docs/releasing.md`, the reproducibility comparison | high (`scripts/**`) |
| 4 | The store listing text and screenshots, `PRIVACY.md`, the README link | low (store listing, docs) |
| 5 | `versionName` 1.0.0, `changelogs/1.txt` and the README status. After it's merged: the tag, then the maintainer runs `scripts/release.py 1.0.0` | high (version bump, changelog) |
| 6 | `docs/fdroid/io.github.dryingday.yml` with the commit and the fingerprint, and the release script's three hashes for `v1.0.0`, for the maintainer to submit | low (docs) |
