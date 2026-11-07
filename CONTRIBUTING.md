# Contributing

Thanks for helping. Drying Day is small, so the rules are short.

## Setting up

- **JDK 21.** Temurin is what CI uses.
- **Android SDK** with the `compileSdk` platform from [gradle/libs.versions.toml](gradle/libs.versions.toml).
  Set `ANDROID_HOME`, or `sdk.dir` in `local.properties`.
- **Python 3**, for the hook tests and the dependency verification script.

```
./gradlew check assembleDebug
```

This runs the tests, Android lint, ktlint, detekt and the check for non-free dependencies, then
builds a debug APK. `./gradlew spotlessApply` fixes formatting. The iOS targets build only with
`-Pdryingday.ios=true`, on macOS.

## Rules that matter

- **F-Droid compatible.** No Google Play Services, Firebase or other non-free libraries; the build
  fails if one appears. Dependencies come from Google Maven and Maven Central only.
- **Private.** No accounts, analytics or tracking. The only network calls go to the weather
  source the user picked. Location is coarse.
- **No real places** in code, tests or fixtures, and no default location.
- **User-facing text** goes in string resources.
- **Times** are computed in the forecast location's time zone, never the device's.

[REVIEW.md](REVIEW.md) has the full list reviewers check.

## Changing dependencies

Every artifact the build downloads is pinned by SHA-256 in
[gradle/verification-metadata.xml](gradle/verification-metadata.xml). After adding or upgrading a
dependency or plugin, regenerate it and look over the diff:

```
python3 scripts/update-verification-metadata.py
git diff gradle/verification-metadata.xml
```

The script also records the Linux, macOS and Windows builds of platform-specific tools, so CI and
F-Droid's build server keep working.

## Pull requests

- Keep each PR small, about ten minutes to review.
- Fill in the [PR template](.github/pull_request_template.md).
- CI ("Check and assemble") must pass, and the branch must be up to date with `main`.
- Some paths are protected (build files, CI, manifests, the review rules); see
  [docs/review-policy.md](docs/review-policy.md). Changes to them need the maintainer's approval.

Much of the work here is done by an AI agent under that policy, which is why the PR template asks
for evidence and review notes. Contributions from people follow the same template.

## Licence

By contributing you agree that your work is released under
[GPL-3.0-or-later](LICENSE).
