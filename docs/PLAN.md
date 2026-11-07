# Drying Day: plan

Answers one question: will the washing dry on the line today? Verdict first, then when to hang it out and bring it in, a few hanging tips, and an indoor plan when outdoors won't finish the job.

## Goals

- A clear verdict for today and the next two days: **Will dry**, **Partly dry, finish indoors** or **Don't bother, dry indoors**.
- Adjusted for the load: thick items (towels, jeans, bedding) need much better conditions than shirts.
- Free and open source (GPL-3.0-or-later), published on F-Droid.
- Android first; built with Kotlin Multiplatform and Compose Multiplatform so an iOS app needs only a thin wrapper.
- Private by design: no account, no backend, no analytics. The only network calls are to the weather source the user picked.

## Non-goals for v1

- AI or LLMs in the app. The verdict is a deterministic rules engine (see Decisions).
- Weather sources other than Open-Meteo. The code is shaped so more can be added.
- Notifications, widgets, iOS builds.

## Decisions

- **No default location.** First launch asks for approximate device location or a place search. Nothing is baked in, and test fixtures come from neutral, well-known places.
- **Rules, not a model.** The rules of thumb are explicit (humidity, wind, temperature, dew point, fog, rain chance, daylight left). A rules engine is deterministic, testable, offline and tiny. An on-device model would add 0.5–2 GB and get numbers wrong; Gemini Nano and hosted APIs aren't F-Droid compatible. AI is used at development time instead: a reference set of real forecasts is labelled with an AI-assisted advisor, disagreements are reviewed by a person, and the agreed verdicts become tests the engine must pass.
- **F-Droid compatible from the first commit.** No Google Play Services, Firebase or proprietary libraries. Location uses the platform `LocationManager`. FLOSS JDK (Temurin). Builds from the command line on Linux. Store metadata lives in `fastlane/metadata/android/`. Aim for reproducible builds.
- **Weather sources behind an interface.** Modelled on [Breezy Weather](https://github.com/breezy-weather/breezy-weather)'s design (a `Source` with feature interfaces, each source in its own package). Breezy is LGPL-3.0: we use it as a design reference only and don't copy code.
- **Open-Meteo attribution** (CC BY 4.0) is shown in the app.
- **App ID:** `io.github.dryingday`.
- **Stack:** Kotlin, Compose Multiplatform, Ktor client, kotlinx.serialization, kotlinx-datetime, multiplatform-settings. Manual dependency wiring, no DI framework. Versions pinned in `gradle/libs.versions.toml`.

## How work is done

Work is split into **bolts**: coherent slices of hours to a day or two, each with a spec in `docs/bolts/`. A bolt ships as several **small PRs**, each reviewable in about ten minutes (aim for under ~300 changed lines, not counting generated files and fixtures).

For each PR:

1. Branch `bolt/NN-short-name` (one per PR), in its own git worktree when bolts run in parallel.
2. Tests first, then code. A Stop hook runs `./gradlew check` and won't let the agent finish while it fails.
3. Review round 1: `/code-review` in a fresh context (bugs).
4. Review round 2: a fresh subagent reviews the diff against the bolt spec, skipping style.
5. Review round 3, for network, location, permissions and release changes: `/security-review`.
6. Fix confirmed findings only; record each finding and what was done in the PR description. Re-run checks.
7. Risk tier assigned and the PR merged or flagged per [review-policy.md](review-policy.md).

## Bolts

| # | Bolt | Delivers |
|---|---|---|
| 0 | Toolchain | JDK, Android SDK, `gh` on the dev machine (done, no PR) |
| 1 | Walking skeleton | Project builds, tests, lints and runs a placeholder screen; CI; risk-tier machinery; contributor docs |
| 2 | Drying rules + reference set | Pure shared engine: verdict, load type, best hang-out and bring-in times, tips, indoor plan; labelled reference forecasts as tests |
| 3 | Weather source + Open-Meteo | `WeatherSource` interface, Open-Meteo client and geocoding, fixture tests, attribution |
| 4 | Location | First-run choice, platform location (coarse), place search, saved places, permission handling |
| 5 | Main screen | Verdict-first UI, load switch, today/tomorrow/day after, offline cache, error states |
| 6 | Release | Icon, fastlane metadata and screenshots, release signing, privacy policy, reproducibility check, draft fdroiddata metadata, `v1.0.0` |

Bolts 2 and 3 can run in parallel once 1 is merged.

## Later

- More weather sources and a source picker.
- Morning "drying day" notification; home-screen widget.
- iOS app.
- Optional bring-your-own AI endpoint (OpenAI-compatible, e.g. a local Ollama) for free-form questions. Off by default; never decides the verdict.
