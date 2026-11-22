# Bolt 8: fewer words, more pictures

Two follow-ups from the maintainer's review of bolt 7 (PR 70). The load switch sits too far down the screen, and the screen still says in sentences what a picture could show. This bolt moves the load choice to the top as a two-way toggle with a weight on each side, and turns the drying window into a bar above the hours row with the hang-out and bring-in times on it. The explanations that the pictures replace move into short notes that open on a tap. The engine and its advice don't change.

## In scope

### Notes that open

One small component, used twice below: a line of text with a mark after it, "▸" closed and "▾" open, that shows or hides the note's text under it when tapped.

- At least 48 dp tall, the whole line tappable. Underlined like the screen's other actions, so it doesn't rely on colour.
- Closed on launch. Open or closed survives rotation (bolt 4's `configChanges` keeps the composition) and switching days. So each note's state is held above the selected day, not inside it: the window note leaves the composition on a day without a bar, and comes back as it was. It isn't stored: no new settings key.
- **Accessibility.** One node with `Role.Button`, its label the line's text, and a state description, "collapsed" or "expanded", from string resources. The mark is decorative, its semantics cleared, as bolt 5's load switch does with "●".

### The load toggle

Replaces bolt 5's load switch. The choices, the default (`HEAVY`), the stored key and value, and the behaviour (re-runs the engine, fetches nothing) are unchanged.

**Where.** Straight under the place, above the days, in every state: `Showing`, `Loading`, `Failed` and `OutOfDate`. The load changes all three days' verdicts, so it goes above the tabs that show them, and it stays put when the forecast arrives. Bolt 9 moves it straight under the sky and the line of cloths.

**What.** Two halves side by side, each at least 48 dp tall:

| Half | Shows |
|---|---|
| Left | The light weight, then "Light" |
| Right | "Heavy", then the heavy weight |

The selected half has a border and bold text; the other has neither. So it isn't carried by colour alone. (Bolt 9 squares its corners and fills the selected half with ink, keeping the border and bold text.) The weights sit on the outer ends, so the toggle reads light to heavy from left to right. At the same font scale as the day tabs stack (1.5), the halves stack too, light first, each keeping its weight at the start.

**Accessibility.** The two halves are a `selectableGroup()`, so TalkBack says which of two it's on. Each half is one node with `Role.RadioButton` and its selected state, labelled "Light load" or "Heavy load" (the existing strings), as the switch is now. The weights are decorative.

**The note**, under the toggle: "Light or heavy?". Open, it shows bolt 5's two hints, each led by its load, and why heavy is the default:

- Light: Shirts, underwear, thin cotton, synthetics.
- Heavy: Towels, jeans, bedding, hoodies. Mixed loads too.
- Heavy loads need better weather than light ones. If you're not sure, pick heavy.

The hints no longer show under each choice.

### The drying window bar

Replaces bolt 7's bar under the hour columns, and the written times. Bolt 9 draws it as a row of washing, placed the same way.

**Where.** Above the hours row, in the same sideways scroll, so it moves with the columns. Shown when the selected day has `hangOut` and `bringIn` (a `WILL_DRY` or `PARTLY_DRY` day) and an hours row. A day with a window always has a row, as the window lies in daylight; a test checks that over the reference set and the engine's tests' forecasts, and if it ever doesn't, the written times are shown as now, so the times are never lost.

**The columns** become one width, the widest column's (still at least 48 dp), so a time maps to a place on the row by its column index and fraction alone. That width depends on the font scale, so the row measures its columns once (a custom `Layout`, or a measured width passed down), and the opening scroll runs after that layout.

**Its span.** From the hang-out to the bring-in as bolt 5 shows them, through `DayAdvice.window()` (the hang-out rounded up to the minute and the bring-in down, both the hang-out's minute when they'd meet), so the bar's ends and its times always agree. Placed by time, not by whole hours: a time falls in the column whose hour holds it, at the fraction of the hour it's at. A bring-in exactly at an hour's end is that column's right edge. A time before the first column's start is clamped to it, and a time after the last column's end to that. Neither happens with the engine's own advice (it never starts a window before `now`, and the first column today is the hour holding `now`), so the clamp is defensive and tested with synthetic input. Today after the hang-out time, the bar starts partway along the first column, at `now`. On the day the clocks change, columns are placed by their instants, so the repeated or skipped hour needs nothing special.

The columns are consecutive hours wherever there's a window: the engine gives no advice for a day with a gap in its usable time (its completeness check), so no gap falls inside a window.

The placement is pure logic, as bolt 7's `dayHours` is: a function from the columns, the window, the column width and the row's width (both in dp) to the bar's start and end in dp along the row, the 6 dp minimum below applied. The screen measures the widths and draws the result.

**The bar.** 6 dp tall, in the text colour, as now. At least 6 dp wide, so a short window or one whose times meet still shows: a bar narrower than that is widened about its centre, then kept inside the row. The first column today is the whole hour holding `now`, so the bar can start partway along it; the part before `now` is left as it is.

**The times.** Above the bar, the two times from `window()`, in `formatTime` in the forecast's zone: the hang-out time over the bar's start, its start edge lined up with it, and the bring-in time over the bar's end, its end edge lined up with it. When the two don't fit side by side over the bar with a gap between them, they're shown as one label, "10:15 to 11:00", centred over the bar and kept inside the row. When `window()` gives the two the same minute, the label is that one time. The label never clips at 200 % font scale.

**Where the row opens.** Each day's row opens with the bar's start in view: scrolled to one column before the bar's first column, or the first column if the bar starts there. A day without a bar opens at its first column, as now. This is also pure logic (the column to open at), with the screen scrolling to it.

**Accessibility.** The bar and its times are one node, before the columns, that reads the existing sentence: "Hang out at 10:15. Bring in by 14:30." The times inside it are decorative. Each column keeps its spoken text from bolt 7, "in the drying window" included, judged by the same overlap as now.

**The written times go.** "Hang out at 10:15. Bring in by 14:30." no longer shows as a line (it stays as the bar's spoken text). On a `PARTLY_DRY` day, "About 70 % dry by then." loses the "then" it pointed at, so it becomes "About 70 % dry by 14:30.", with `window()`'s bring-in.

**The note**, under the row: "What's the drying window?". Open: "The bar runs from when the washing starts to dry until it's dry, or until damp air, rain or sunset would start to undo the work. Hang it out at the start and bring it in by the end." (Bolt 9 rewords it for the row of washing.) "Times are in the place's own time zone." goes straight under the row, above the note, so it's always visible. It's shown whenever there's a row and the device's offset differs, by bolt 5's test, not only when there's a window: a don't-bother day's hour labels are in the place's zone too.

### The screen

Changes to the order bolt 5 and bolt 7 left, from the top:

1. **The place**, unchanged.
2. **The load toggle and its note**, new here.
3. **The days**, unchanged.
4. **The selected day:**
   - the verdict, unchanged;
   - on a `PARTLY_DRY` day, "About 70 % dry by 14:30.";
   - the rain line, unchanged;
   - the bar and the hours row (or, if a day with a window ever had no row, the written times, as now);
   - "Times are in the place's own time zone.", when there's a row (or the written times) and the zones differ;
   - the drying window note, when there's a bar;
   - Why, Tips and Indoors, unchanged.
5. **The forecast's status and Refresh**, unchanged.
6. **The credit**, unchanged.

Without a usable forecast: the place, the load toggle and its note, the message for the status, and the credit.

Bolt 5's "Design system" still holds: foundation only, at least 48 dp for anything tappable, light and dark, nothing carried by colour alone, and no clipping at 200 % font scale.

### The pictures

Two weights, light and heavy, in bolt 7's style: line drawings in one colour on a 24 dp grid with 1.5 strokes, tinted with the text colour. The same shape at two sizes: the light weight small in the grid, the heavy one filling it, so the difference is in the size. SVG sources in `art/`, vector drawables in `shared/src/commonMain/composeResources/drawable/`, through `painterResource`, as bolt 7. Licensed with the app. An exhaustive `when` maps each `Load` to its picture.

PR 1 updates the contact sheet (`art/pictures-sheet.png`) with the two, in light and dark, next to the existing twelve. PR 1 is medium, so it merges without the maintainer; their first look at the weights is on the screen in PR 3, which can still change them.

### Text

In string resources, drafted with the maintainer's voice profile (the reason before the rule, no dashes); the maintainer can still change any of them in PR 2:

| Key | Text |
|---|---|
| Toggle halves | Light / Heavy (spoken: the existing "Light load" / "Heavy load") |
| Load note | Light or heavy? |
| Load note, open | Light: Shirts, underwear, thin cotton, synthetics. / Heavy: Towels, jeans, bedding, hoodies. Mixed loads too. / Heavy loads need better weather than light ones. If you're not sure, pick heavy. |
| Window note | What's the drying window? |
| Window note, open | The bar runs from when the washing starts to dry until it's dry, or until damp air, rain or sunset would start to undo the work. Hang it out at the start and bring it in by the end. |
| Dryness | About %1$s dry by %2$s. (replaces "About %1$s dry by then.") |
| Bar, one label | %1$s to %2$s |
| Note state (spoken) | collapsed / expanded |

The hint strings `load_light_hint` and `load_heavy_hint` are reused in the open note, led by the half's name.

### Release

In `androidApp/build.gradle.kts`, `versionName` 1.2.0 and `versionCode` 4, with `fastlane/metadata/android/en-US/changelogs/4.txt`. The four store screenshots are retaken from the final main. After the PR is merged, the tag goes on, and the maintainer runs `scripts/release.py 1.2.0`, as `docs/releasing.md` sets out.

### Network, permissions, dependencies

- **Network:** no change.
- **Permissions:** none new, and no manifest change.
- **Dependencies:** none new and no catalogue change.
- **Stored data:** no change. The load is stored as before; the notes' state isn't stored.

## Out of scope

- Any change to the engine, its rules, thresholds or verdicts, or to the forecast request and cache.
- The place's name for a device fix (it needs reverse geocoding, a new request), pictures of how to hang clothes, a settings page for equipment, and a Breezy-style place switcher. Each stays in `docs/PLAN.md`'s "Later".
- Making the bar or the columns tappable.
- Translations, notifications and widgets.

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- Bar placement tests:
  - hang-out and bring-in mid-hour, on the hour, and a bring-in exactly at an hour's end;
  - both in the same column, and a window narrower than 6 dp widened to 6 dp, in the middle of the row and at its start and end edges;
  - the bar's ends taken from `window()`, including the two times meeting in one minute;
  - a hang-out before the first column and a bring-in after the last, with synthetic advice, clamped;
  - both daylight-saving changes, in the forecast's zone at a zone different from the device's;
  - no bar for a `DONT_BOTHER` day, a `null` day, polar night and today after sunset.
- Where-the-row-opens tests: the bar starting in the first column, in a later column, and no bar.
- A test that every day with a window has an hours row, over the reference set and the engine tests' forecasts, for both loads.
- Text tests: the dryness line with `window()`'s bring-in, in the forecast's zone; the one-label form, and the single time when the two meet; the time-zone note on a don't-bother day with a row.
- A test that every `Load` has a picture, through the exhaustive mapping.
- The existing load tests (default, round trip, unknown stored value) pass unchanged.
- No real places in tests, fixtures or screenshots, beyond the reference set's.
- On the emulator (API 36), with a debug build (no new JSON decoding, so the release APK isn't required). Recorded in PR 3 with screenshots:
  - a will-dry, a partly-dry and a don't-bother day, in light and dark;
  - both notes open and closed, TalkBack reading "collapsed" and "expanded";
  - a note left open across rotation and across switching days (the window note opened on a will-dry day is still open after going to a don't-bother day, which has no note, and back);
  - the toggle switched, both ways;
  - a short window shown as one label (when the label form is chosen depends on measured text, so it's checked here, not in a unit test), and a long one with the row opened partway along;
  - today after the hang-out time, the bar starting partway along the first column;
  - the loading, failed and out-of-date states with the toggle at the top (out of date by the clock moved forward, as in bolt 5);
  - 200 % font scale with no clipping, the toggle stacked;
  - TalkBack reading the toggle ("1 of 2"), a note with its state, and the bar.

  As in bolt 7, live data gives whatever the day brings, so the PR says which verdict was live and shows the others with fixtures through an uncommitted fake source.
- PR 4: `versionCode` 4 and `versionName` 1.2.0, `changelogs/4.txt` within F-Droid's length (the existing `scripts/` test). `v1.2.0` is on GitHub Releases with the signed APK and its SHA-256.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec, the lines it changes in bolts 5 and 7 (the screen order, the load switch, the written times, the bar under the columns), and bolt 8 in `docs/PLAN.md`, with its two items out of "Later" | high (a visual design) |
| 1 | The two weights: SVG sources, vector drawables, the mapping and its test, the contact sheet | medium (art in the style bolt 7 approved; the maintainer sees it on screen in PR 3) |
| 2 | The logic and the text: bar placement, where the row opens, the window-has-a-row test, the new strings and the dryness line, with tests | medium (UI copy, time display) |
| 3 | The screen: the notes, the toggle at the top, the bar and its times, the new order, the emulator run | high (asks the maintainer, from the screenshots: "Is this fewer words, with the verdict still first?") |
| 4 | 1.2.0: version, changelog, retaken screenshots. After it's merged: the tag, then `scripts/release.py 1.2.0` | high (version bump, store listing) |
