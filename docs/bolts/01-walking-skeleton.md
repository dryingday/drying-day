# Bolt 1: walking skeleton

The whole pipeline works end to end before any feature exists: the project builds, tests and lints locally and in CI, a placeholder app runs on a phone, and PRs are risk-tiered.

## In scope

- Kotlin Multiplatform project with a `composeApp` module: `commonMain`, `commonTest`, `androidMain`, `iosMain` (iOS targets configured; skipped where Xcode isn't available, including Linux CI and F-Droid's build server).
- Placeholder Compose screen ("Drying Day") from `commonMain`.
- One trivial shared unit test so `commonTest` runs.
- Gradle wrapper with verified distribution checksum; versions in `gradle/libs.versions.toml`; latest stable Kotlin, AGP and Compose Multiplatform at time of build.
- Android: `minSdk 26`, `targetSdk`/`compileSdk 36`, app ID `io.github.dryingday`, no permissions yet.
- ktlint and detekt wired into `./gradlew check`.
- GitHub Actions: `./gradlew check assembleDebug` on Ubuntu with Temurin 21.
- A check that fails the build if a Google Play Services, Firebase or other known non-free dependency appears.
- Review machinery: `.github/CODEOWNERS`, the `risk:low|medium|high` labels, the `main` ruleset, tier-reviewer and spec-reviewer subagents in `.claude/agents/`, the merge-blocking hook, the Stop hook.
- `README.md`, `CLAUDE.md`, `CONTRIBUTING.md`, PR template.

## Out of scope

Any weather, location or drying logic. App icon and store metadata (bolt 6).

## Acceptance criteria

- `./gradlew check assembleDebug` passes locally and in CI from a clean checkout.
- The debug APK installs on a phone or emulator and shows the placeholder screen.
- Adding `com.google.android.gms:play-services-location` to a branch fails the build.
- A PR touching `.github/` requires code owner approval; a docs-only PR doesn't.
- `gh pr merge` on a PR not labelled `risk:low` is blocked by the hook.
- PRs are opened by the agent's machine account and can be approved by the maintainer.
- No personal data (names of places, addresses) in the repo.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | Bootstrap commit straight to `main`: LICENSE, README stub, REVIEW.md, `docs/` (plan, review policy, this spec). Reviewed by the maintainer as files before pushing. | high |
| 1 | Gradle skeleton: wrapper, version catalog, settings, empty `composeApp` building for Android | high |
| 2 | Placeholder screen + first shared test; iOS entry point stub | medium |
| 3 | ktlint + detekt in `check`, and the non-free dependency check | high |
| 4 | CI workflow | high |
| 5 | CODEOWNERS, labels, ruleset, tier and spec reviewer subagents, merge-blocking hook, Stop hook | high |
| 6 | CLAUDE.md, CONTRIBUTING.md, PR template, README | high (CLAUDE.md, `.github/`) |

Bolt 1 is mostly protected paths, so nearly all of it needs the maintainer. From bolt 2 onwards most PRs should be low or medium.
