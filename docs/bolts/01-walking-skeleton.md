# Bolt 1: walking skeleton

The whole pipeline works end to end before any feature exists: the project builds, tests and lints locally and in CI, a placeholder app runs on a phone, and PRs are risk-tiered.

## In scope

- Two modules, following the AGP 9 layout (AGP 9 no longer builds an Android app from a multiplatform module):
  - `shared`: Kotlin Multiplatform library (`com.android.kotlin.multiplatform.library`) with `commonMain`, `commonTest`, `androidMain`, `iosMain`. All logic and Compose UI lives here.
  - `androidApp`: thin Android application module (activity, manifest, resources).
  - iOS targets are behind `dryingday.ios=true` in `gradle.properties`, off by default: they need macOS with Xcode, and are skipped on Linux CI and F-Droid's build server. A later `iosApp/` Xcode project is the iOS equivalent of `androidApp`.
- Placeholder Compose screen ("Drying Day") from `commonMain`.
- One trivial shared unit test so `commonTest` runs.
- Gradle wrapper with verified distribution checksum; versions in `gradle/libs.versions.toml`; latest stable Kotlin, AGP and Compose Multiplatform at time of build.
- Android: `minSdk 26`, `targetSdk`/`compileSdk 37`, app ID `io.github.dryingday`, no permissions yet. Cloud backup and device-to-device transfer are switched off (`allowBackup=false` plus data extraction rules excluding every domain): nothing the app stores should leave the device without the user knowing.
- ktlint and detekt wired into `./gradlew check`.
- GitHub Actions: `./gradlew check assembleDebug` on Ubuntu with Temurin 21.
- A check that fails the build if a Google Play Services, Firebase or other known non-free dependency appears.
- Dependency verification: SHA-256 checksums in `gradle/verification-metadata.xml` for every artifact Gradle resolves (dependencies, plugins, lint and formatting tools), so a changed or unexpected artifact fails the build. Platform-specific tools are pinned for every machine that builds the project: AAPT2 for Linux, macOS and Windows, Kotlin/Native for both Mac architectures. `scripts/update-verification-metadata.py` regenerates the file after a dependency change. Not covered: downloads outside Gradle's dependency resolution, such as the Android SDK manager and Kotlin/Native's toolchain bundles from download.jetbrains.com.
- Review machinery: `.github/CODEOWNERS`, the `risk:low|medium|high` labels, the `main` ruleset, tier-reviewer and spec-reviewer subagents in `.claude/agents/`, the merge-blocking hook, the Stop hook.
- `README.md`, `CLAUDE.md`, `CONTRIBUTING.md`, PR template.

## Out of scope

Any weather, location or drying logic. App icon and store metadata (bolt 6).

## Acceptance criteria

- `./gradlew check assembleDebug` passes locally and in CI from a clean checkout.
- The debug APK installs on a phone or emulator and shows the placeholder screen.
- Adding `com.google.android.gms:play-services-location` to a branch fails the build.
- A PR touching `.github/` requires code owner approval; a docs-only PR doesn't.
- The agent merges, approves and applies the ruleset only through `.claude/hooks/maintainer.py`, and the hook refuses any other way. `maintainer.py` refuses unless the maintainer has approved the PR's current commit (an approval starting "Approved by the agent" doesn't count), or the PR carries one risk label, `risk:low` or `risk:medium`, and touches no protected path (the conditions in full are in the policy's Enforcement section).
- PRs are opened by the agent's machine account and can be approved by the maintainer.
- No personal data (names of places, addresses) in the repo.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | Bootstrap commit straight to `main`: LICENSE, README stub, REVIEW.md, `docs/` (plan, review policy, this spec). Reviewed by the maintainer as files before pushing. | high |
| 1 | Gradle skeleton: wrapper, version catalog, settings, `shared` and `androidApp` modules building a debug APK | high |
| 2 | Placeholder screen + first shared test; iOS entry point stub | medium |
| 3 | ktlint + detekt in `check`, and the non-free dependency check | high |
| 3b | Dependency verification metadata, proposed in PR 1's security review; pins what PRs 1–3 brought in | high |
| 4 | CI workflow | high |
| 5a | CODEOWNERS, the `main` ruleset as `.github/rulesets/main.json`, review-policy updates for the agent's accounts (the `risk:*` labels already exist) | high |
| 5b | Tier and spec reviewer subagents, merge-blocking hook, Stop hook | high |
| 6 | CLAUDE.md, CONTRIBUTING.md, PR template, README | high (CLAUDE.md, `.github/`) |

Bolt 1 is mostly protected paths, so nearly all of it needs the maintainer. From bolt 2 onwards most PRs should be low or medium.
