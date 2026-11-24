# Bolt 10: what you dry with

The indoor plan assumes everyone has the same kit: an airer, and maybe a dehumidifier ("Use a dehumidifier, or a small room with the door closed."). On a "Dry indoors" day, which is most days in winter, that's the advice people read most, and half of it may not apply to them. This bolt lets them say what they have, and the indoor plan uses it. The verdict, the times, the tips and the forecast don't change.

Until someone says what they have, the plan is exactly bolt 2's, so nothing changes for anyone who never opens the new screen.

## In scope

### The kit

Four things, in this order everywhere (the enum, the screen, the stored value):

| Code | On the screen |
|---|---|
| `AIRER` | Clothes airer |
| `HEATED_AIRER` | Heated airer |
| `DEHUMIDIFIER` | Dehumidifier |
| `TUMBLE_DRYER` | Tumble dryer |

`enum class Equipment` in the engine package. The engine takes the kit as `Set<Equipment>?`: `null` means not said, and an empty set means none of the four.

Not in the list: a radiator (assumed, as now: "Not on a radiator. Near one is fine." is always given), a washer-dryer (it's a tumble dryer here), a drying cabinet, a heat pump dryer as distinct from a tumble dryer.

### The indoor plan

`advise(forecast, load, now, kit: Set<Equipment>? = null)`, passed on to the internal `indoorPlan` in `Tips.kt`. The default keeps every existing caller, the reference set and its tests as they are. When the plan is given (`PARTLY_DRY` after bring-in, and `DONT_BOTHER`) and over the same indoor time and humidity mean as bolt 2:

| Step | When | Text |
|---|---|---|
| `HEATED_AIRER_SPACED` | Kit has `HEATED_AIRER` | Use the heated airer, with space between items. |
| `AIRER_SPACED` | Kit is `null`, or has `AIRER` and not `HEATED_AIRER` | Use an airer, with space between items. (unchanged) |
| `HANGERS_SPACED` | Kit is set and has neither airer | Hang things on hangers from a rail or a door frame, with space between them. |
| `OPEN_WINDOW` | Mean outdoor relative humidity below 70 %, whatever the kit | Open a window, as the air outside is dry enough to help. (unchanged) |
| `DEHUMIDIFIER_OR_CLOSED_ROOM` | The same mean 70 % or more, and kit `null` | Keep windows shut. Use a dehumidifier, or a small room with the door closed. (unchanged) |
| `DEHUMIDIFIER_CLOSED_ROOM` | The same mean 70 % or more, and kit has `DEHUMIDIFIER` | Keep windows shut. Run the dehumidifier in a small room with the door closed. |
| `WARM_ROOM_AIRED` | The same mean 70 % or more, and kit is set without `DEHUMIDIFIER` | Dry it in the warmest room you have, and open the window wide for a few minutes now and then to let the damp out. |
| `NOT_ON_RADIATOR` | Always | Not on a radiator. Near one is fine. (unchanged) |
| `TURN_HEAVY_ITEMS` | `HEAVY` load | Turn heavy items now and then. (unchanged) |
| `TUMBLE_TO_FINISH` | Kit has `TUMBLE_DRYER` | If it's still damp tonight, finish it in the tumble dryer. |

- Exactly one of the first three is given, and at most one of the four humidity steps, so the plan has at most five lines.
- A kit with a dehumidifier or a tumble dryer but neither airer gets `HANGERS_SPACED`.
- On a dry day (`OPEN_WINDOW`), a dehumidifier isn't mentioned: an open window does the same job for free.
- The humidity steps follow bolt 2's rule on missing hours: if any of the indoor time is missing, none of `OPEN_WINDOW`, `DEHUMIDIFIER_OR_CLOSED_ROOM`, `DEHUMIDIFIER_CLOSED_ROOM` and `WARM_ROOM_AIRED` is given.
- Order on screen is the enum's, as now: `HEATED_AIRER_SPACED`, `AIRER_SPACED`, `HANGERS_SPACED`, `OPEN_WINDOW`, `DEHUMIDIFIER_OR_CLOSED_ROOM`, `DEHUMIDIFIER_CLOSED_ROOM`, `WARM_ROOM_AIRED`, `NOT_ON_RADIATOR`, `TURN_HEAVY_ITEMS`, `TUMBLE_TO_FINISH`.
- No new threshold. 70 % and the six-hour indoor time are bolt 2's, unchanged.
- **Why `WARM_ROOM_AIRED`.** Without a dehumidifier, a small closed room fills with the damp coming off the washing and drying stalls. Outdoor air at 70 % or more is still often drier, in water per cubic metre, than warm indoor air near wet washing, so a short, wide opening swaps damp air for drier air without losing much heat. That's true when it's colder outside than in, which is when people dry indoors; on a warm, muggy day it helps less, and the step doesn't claim to know which day it is ("now and then" carries no number for the same reason).

### Stored

- Under the key `equipment` in the existing private preferences file, `settings`, next to `load`: the codes of what's ticked, comma-separated, in the enum's order, for example `AIRER,DEHUMIDIFIER`. An empty string is "none of the four". No key is "not said".
- On reading, unknown codes are dropped and the rest kept; a value with only unknown codes is an empty set, not "not said". Duplicates count once.
- It isn't location or personal data, and the file is already out of backups (`allowBackup` off, `data_extraction_rules.xml` excluding every shared-preferences domain). No manifest change.
- Changing the kit re-runs the engine on the forecast already held and fetches nothing, as the load does.

### The screen

A new screen, "What you dry with", in the look bolt 9 gave the places and search screens: its palette, type and square buttons.

From the top:

1. "Drying advice", the way back, as on the places screen. The system back gesture does the same.
2. The heading: "What you dry with".
3. One line: "Tick what you have at home and the indoor plan will use it. This stays on your phone."
4. The four, each a row with a checkbox and its name, at least 48 dp tall, the whole row tappable.

It's a screen in the Compose app, a new value of `App`'s `Screen`, as places and search are: no new Activity. Each tap saves straight away; there's no Save button. The first tap is what turns "not said" into a set kit, so a person who opens the screen and leaves without ticking anything still has bolt 2's plan.

**Getting there.** Two ways in, both on the main screen:

- The last line of the Indoors section (shown whenever there's an indoor plan): "Say what you dry with" while it's not said, "Change what you dry with" once it is. Underlined, at least 48 dp tall.
- At the foot of the screen, above the credit, in every state (`Showing`, `Loading`, `Failed`, `OutOfDate`): "What you dry with". So it's findable on a will-dry day, and after the first time, without waiting for a wet day.

**Accessibility.** The heading is a heading. Each row is one node with `Role.Checkbox`, its label the item's name and its checked state; the checkbox inside it has its own semantics cleared. The two links are `Role.Button`. No clipping at 200 % font scale.

### Text

In string resources, drafted with the maintainer's voice profile (the reason before the rule, no dashes); the maintainer can still change any of them in PR 2:

| Key | Text |
|---|---|
| Screen heading, foot link | What you dry with |
| Screen line | Tick what you have at home and the indoor plan will use it. This stays on your phone. |
| Items | Clothes airer / Heated airer / Dehumidifier / Tumble dryer |
| Indoors link, not said | Say what you dry with |
| Indoors link, said | Change what you dry with |
| The way back | Drying advice (the existing string) |
| The new steps | as in the table under "The indoor plan", in `forecast_codes.xml` with the existing steps |

The screen's strings go in `strings.xml`.

### Release

In `androidApp/build.gradle.kts`, `versionName` 1.4.0 and `versionCode` 6, with `fastlane/metadata/android/en-US/changelogs/6.txt`. The store screenshots are retaken only if the main screen in them changed (the foot link); a screenshot of the new screen isn't added. After the PR is merged, the tag goes on, and the maintainer runs `scripts/release.py 1.4.0`, as `docs/releasing.md` sets out. Then a separate PR moves the F-Droid recipe in `docs/fdroid/` to 1.4.0's commit and version, as for 1.3.0 (PR 88).

### Network, permissions, dependencies

- **Network:** no change.
- **Permissions:** none new, and no manifest change.
- **Dependencies:** none new and no catalogue change.
- **Stored data:** one new key, `equipment`, as above.

## Out of scope

- Any change to the verdict, the drying window, the tips, the thresholds, the reasons, or the forecast request and cache. Owning a tumble dryer or a dehumidifier never changes whether the washing goes out.
- Asking about the kit during first run.
- Pictures of the kit (bolt 8's style would fit; it stays for later).
- How much the kit speeds up indoor drying, or an indoor finish time. The engine has no indoor model, and this bolt doesn't add one.
- Energy cost, or nudging people away from the tumble dryer.
- The place's name for a device fix, pictures of how to hang clothes, a Breezy-style place switcher. Each stays in `docs/PLAN.md`'s "Later".

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- Engine tests:
  - with `kit = null`, every day's indoor plan over the reference set and the engine tests' forecasts, both loads, equals what bolt 2's rules give (the existing `IndoorPlanTest` cases pass unchanged);
  - each row of the table: each airer case (heated wins over plain, neither gives hangers), each humidity case with a dehumidifier, without one and not said, and the tumble dryer step only with a tumble dryer;
  - an empty set: hangers, `WARM_ROOM_AIRED` on a humid day, no tumble dryer step;
  - a dehumidifier alone and a tumble dryer alone each give hangers;
  - a dehumidifier on a dry day gives `OPEN_WINDOW` and nothing about the dehumidifier;
  - missing indoor hours: none of the four humidity steps, whatever the kit;
  - the kit never changes a verdict, a window, a dryness, a reason or a tip (every day of the reference set, both loads, `null` against all sixteen kits);
  - a `WILL_DRY` day has no plan whatever the kit.
- Storage tests, over `MapSettings`: no key is `null`; an empty string is the empty set; a round trip of each of the sixteen kits; the stored order is the enum's whatever the order ticked; unknown codes dropped, and only unknown codes giving the empty set; duplicates once.
- Text tests: every `IndoorStep` has a string, through the exhaustive mapping; the Indoors link's two wordings.
- The existing load tests pass unchanged.
- No real places in tests, fixtures or screenshots, beyond the reference set's.
- On the emulator (API 36), with a debug build. Recorded in PR 3 with screenshots:
  - a don't-bother day's Indoors section not said, then with a dehumidifier and an airer, then with nothing ticked, in light and dark;
  - a partly-dry day's plan with a tumble dryer;
  - the screen, reached from both links, back by "Drying advice" and by the back gesture;
  - the kit kept across a restart, and across rotation;
  - 200 % font scale with no clipping;
  - TalkBack reading a row ("Dehumidifier, checkbox, ticked") and the heading as a heading.

  As before, live data gives whatever the day brings, so the PR says which verdict was live and shows the others with fixtures through an uncommitted fake source.
- PR 4: `versionCode` 6 and `versionName` 1.4.0, `changelogs/6.txt` within F-Droid's length (the existing `scripts/` test). `v1.4.0` is on GitHub Releases with the signed APK and its SHA-256.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec, a line under bolt 2's indoor plan table pointing here, and bolt 10 in `docs/PLAN.md` with its item out of "Later" | high (new indoor advice) |
| 1 | The engine: `Equipment`, the new `IndoorStep`s, `advise`'s `kit`, the rules and their tests | medium (rule changes this spec names, no thresholds) |
| 2 | Storage and text: reading and writing `equipment`, the new strings and their tests; `ForecastState` passing the kit to `advise` | medium (stored data and UI copy this spec names) |
| 3 | The screen and the two links, navigation, the emulator run | high (asks the maintainer, from the screenshots: "Is it findable, and does the plan read right for your own kit?") |
| 4 | 1.4.0: version, changelog, screenshots if the main screen changed. After it's merged: the tag, then `scripts/release.py 1.4.0` | high (version bump, store listing) |
| 5 | The F-Droid recipe in `docs/fdroid/`: 1.4.0's commit, `versionName` and `versionCode` | high (protected path) |
