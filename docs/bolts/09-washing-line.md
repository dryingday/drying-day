# Bolt 9: the washing line

The maintainer wasn't happy with how the app looks: it's plain foundation components in the system font, with nothing in it of washing or weather. This bolt gives the main screen one idea, the washing line, and a look the other screens share. Each day is a cloth pegged on a line, and the chosen day hangs large with its verdict printed on it. The sky behind the line is the day's weather. The drying window becomes a row of washing pegged on a second line above the hours. The engine, its advice and the forecast don't change.

The design was settled with the maintainer in an interactive mock, kept next to this spec as [09-washing-line.html](09-washing-line.html). It shows three phones in light and dark. Where the mock and this spec differ, this spec wins.

## In scope

### Type

Two typefaces, bundled with the app, both under the SIL Open Font License 1.1 with no Reserved Font Name:

| Face | Used for | Files |
|---|---|---|
| Big Shoulders Display (Patric King, Xotype) | The verdict on the open cloth, the day names on the cloths, the section headings | `BigShouldersDisplay-Bold.ttf`, `BigShouldersDisplay-ExtraBold.ttf`, from `github.com/xotypeco/big_shoulders` at commit `41153e6fe01d218e933919a1d08c8e45065bc8fe`, path `Big-Shoulders/fonts/ttf/static/` |
| Public Sans (USWDS) | Everything else: sentences, tips, times, buttons, labels | `PublicSans-Regular.ttf`, `PublicSans-SemiBold.ttf`, `PublicSans-Bold.ttf`, from `github.com/uswds/public-sans` at commit `c7923167a592d941646f99fb7b5fba17aa7d69e1`, path `fonts/ttf/` |

The commits are the ones Google Fonts builds each family from (its `METADATA.pb`, read on 2026-11-22). Static files, not the variable ones, so nothing depends on variable-font support. About 430 KB in all.

- The files go unchanged in `shared/src/commonMain/composeResources/font/`, used through `Font(Res.font.…)` from the resources library the app already uses. No new dependency and no build script change.
- Each family's `OFL.txt`, unchanged from the same commit, goes in `shared/src/commonMain/composeResources/files/licenses/`, so the licence ships in the APK with the fonts as the OFL asks, and is listed in the repository's licence notes. The fonts stay under the OFL; the app stays GPL-3.0-or-later. The OFL allows bundling with any software, and F-Droid accepts OFL fonts.
- PR 1 records each file's SHA-256 against the file at that commit.

Sizes, in sp so they follow the font scale: the open cloth's verdict 48, Big Shoulders ExtraBold, line height 1.02 (tighter and a "y" touches the line below); the day names on the cloths 15, Bold; section headings 22, Bold. Body text 15 Public Sans Regular, labels and buttons 13 to 14 SemiBold or Bold. The verdict has a second print 2 dp right and 2 dp down in the laundry blue at 30 %, like a stamped linen mark. It's decorative and drawn behind the text.

### Colour

One palette for every screen, in one place in the code, replacing the colours each screen sets now. Light and dark follow the system, as now.

| Token | Light | Dark | Used for |
|---|---|---|---|
| Page | `#F3F6F9` | `#14171A` | Below the sky, and every other screen |
| Ink | `#16202B` | `#E8EAED` | Text on the page |
| Muted | `#4A5666` | `#A3ABB6` | Secondary text, the second line |
| Rule | `#C3CDD7` | `#33383F` | Dividers |
| Laundry blue | `#2F5F8A` | `#9CC2E3` | The verdict's second print, focus rings |
| Cloth | `#FFFFFF` | `#ECE8DF` | The cloths, in both modes |
| Cloth ink | `#16202B` | `#16202B` | Text on a cloth, the garments' outlines |
| Peg wood | `#C99A5B` | `#B9893F` | Pegs and the tips' bullets |
| Sun | `#FFD84D` | `#F4C84A` | The sun in the sky |
| Cloud | `#EEF4F9` | `#6D7C8C` | The cloud over the sun |
| Dark cloud | `#2F3C4A` | `#0C1117` | The cloud on a don't-bother day, at 55 % |
| Drop | `#DDF0FC` | `#CFE8F8` | Drips and spray, outlined in the drop edge |
| Drop edge | `#2F5F8A` | `#0E1A28` | |
| Garments | `#FFFFFF`, `#9CC2E3`, `#E8D5B0`, `#F2B8A2` | the same | The row of washing |

Each verdict has a sky, and text on the sky uses that sky's ink:

| Verdict | Sky, light | Ink | Sky, dark | Ink |
|---|---|---|---|---|
| `WILL_DRY` | `#62B3EA` | `#0E1A28` | `#1F5FA8` | `#F4F7FA` |
| `PARTLY_DRY` | `#A9BCCB` | `#0E1A28` | `#435263` | `#F4F7FA` |
| `DONT_BOTHER` | `#4A5B6C` | `#F4F7FA` | `#151C25` | `#F4F7FA` |

A `null` day has the `PARTLY_DRY` sky with no picture in it. Every pair of text and its background is at least 4.5:1, and PR 2 records each ratio in a test that fails if one drops below.

### The main screen

From the top:

1. **The sky.** The full width of the screen, from the top edge to just below the cloths, in the selected day's sky colour. Behind everything in it, one picture by verdict, drawn on the right:
   - `WILL_DRY`: the sun, with a soft ring;
   - `PARTLY_DRY`: the sun half behind a cloud;
   - `DONT_BOTHER` (and today when its only reason is `DAY_OVER`): a dark cloud across the top. When the day has a rain line ("Rain likely from 09:32"), rain falls, as set out under Motion.

   Changing day crossfades the sky's colour over 400 ms and swaps the picture.

   In the sky, from the top: **the place**, as now (its name, tappable, opening the places screen), in the sky's ink; then **the line** with the three cloths.
2. **The load toggle and its note**, under the sky, unchanged from bolt 8 except that the halves have square corners and a 2 dp ink border, the selected half filled with ink and its text in the page colour. (Bolt 8 put the toggle above the days. Here the line and the sky read as one picture, so the toggle goes straight under it, still above everything the load changes on the screen except the cloths.)
3. **The washing and the hours**: the hours row from bolt 7, with the row of washing above it in place of bolt 8's bar (below). Then the time-zone line and the drying window note, as bolt 8 has them.
4. **Why, Tips and Indoors**, as now, restyled: the heading in Big Shoulders, each line led by a small peg in peg wood (6 by 14 dp) in place of a bullet, and the lines divided by a dashed rule, like stitching. No cards and no boxes.
5. **The forecast's status, Refresh and the credit**, as now. Refresh gets the toggle's square border.

Without a usable forecast: the sky in the `PARTLY_DRY` colour with no picture, the place, the empty line, then the load toggle and its note, the message for the status, and the credit.

### The line and the cloths

The line is a 2 dp rule across the sky's full width in the sky's ink at 80 %. Three cloths hang from it, left to right Today, Tomorrow, the day after, 8 dp apart.

**A small cloth**, for a day that isn't selected: 62 dp wide, its name at the top, its wet patch and its drips. The name is short so it fits: "Today", then the weekday's three-letter name ("Mon", "Tue") for the other two. **The open cloth**, the selected day: the rest of the width. From the top:

- the day's full name: "Today", "Tomorrow", or the weekday ("Tuesday");
- the verdict (below, under Text), as a heading;
- the sentence: on a `PARTLY_DRY` day the dryness line, then the rain line; otherwise the rain line, as bolt 7 and bolt 8 have them. A `null` day: "There isn't enough forecast for this day yet." and nothing else;
- a dashed hem 12 dp above the bottom edge.

**Every cloth** is drawn, not an image: the cloth colour with a faint weave (1 dp lines every 3 dp both ways, black at 3 %), a hard shadow 3 dp right and 4 dp down (black at 24 %), and two pegs, 8 by 19 dp, at its top corners, clipping it to the line.

**The wet patch** is how much of the day's washing is still wet when it comes in: its height is `100 − dryness` % of the cloth's height, measured up from the bottom edge. So a will-dry cloth is plain, a 76 %-dry cloth is damp in its bottom 24 %, and a don't-bother cloth is mostly or all wet. The patch is `#1C3A5C`, from 40 % at the bottom edge to 16 % at its top, with a gently wavy top edge. A `null` day's cloth has no patch. The text on a fully wet cloth keeps 4.5:1 against the patch's darkest point, and PR 2's contrast test includes it.

**The drips** hang from the bottom edge of a wet cloth and fall: on a `PARTLY_DRY` cloth, one when small and three when open; on a `DONT_BOTHER` cloth, three when small and eight when open. A will-dry cloth doesn't drip. Each is a drop 9 by 13 dp that swells at the hem, falls about 74 dp and fades, on its own loop of 1.8 to 3.6 s, so they don't fall together.

**Choosing a day.** The whole cloth is the target, at least 48 dp each way. Tapping a small cloth opens it and closes the open one: the widths change over 400 ms. The open cloth's text is laid out at its final width from the start and revealed as the cloth widens, fading in over 300 ms after 180 ms, so its lines never re-wrap while it moves. Tapping the open cloth again just moves it (below). The selection rules are bolt 5's: today on launch and when `Showing.today` changes, kept across rotation.

**At 1.5 font scale and above** (where the day tabs stack now) the cloths hang one under another, each the full width with its own line above it, in the same order. The open one shows everything above; the others their full name, wet patch and drips. Nothing clips at 200 %.

**Accessibility.** The line is a `selectableGroup()`; each cloth is one node with `Role.Tab` and its selected state, labelled with the day's full name and verdict, "Tomorrow, Finish indoors" (a `null` day: the name alone). The open cloth's verdict is also a heading, and its sentence is read after it. The weave, pegs, wet patch, drips, sky picture and rain are decorative, their semantics cleared. The wet patch and drips repeat what the written verdict says, so nothing is carried by them alone.

### The row of washing

Replaces bolt 8's bar. Everything else about it is bolt 8's: it's placed by bolt 8's placement function, in the same sideways scroll as the hours, it has the same two times (or one label) above its ends, the row opens at its start, and it's read as one node with the same sentence, "Hang out at 10:15. Bring in by 14:30."

A 2 dp line, in the muted colour, runs the row's full width. Between the bar's start and end as bolt 8 places them, garments hang from it, each with its own pegs:

| Garment | Size (dp) | Pegs at (dp from its left) |
|---|---|---|
| T-shirt | 26 × 22 | 5, 17 |
| Trousers | 18 × 30 | 2, 11 |
| Shorts | 20 × 17 | 3, 12 |
| Sock | 11 × 20 | 3 |
| Tea towel | 22 × 22 | 3, 14 |

Pegs here are 3.5 by 8 dp, in scale with the clothes. Each garment is outlined 1.3 dp in the cloth ink and filled with one of the four garment colours.

**Which garments**, as pure logic: a function from the span's width in dp and a seed (the day's date) to a list of garments and their x positions. It picks garments and colours with a fixed pseudo-random sequence from the seed, adding them while they fit with at least 4 dp between them, then spreads them so the first starts at the span's start and the last ends at its end. So a day always hangs the same washing, the row fills its window exactly, and nothing is cut off. A span narrower than the narrowest garment (the sock) hangs one sock centred on the span, kept inside the row, so a short window still shows. The row is worked out again when its width changes (font scale, rotation).

The space above the hours grows to fit the trousers, the tallest garment, with the times above the line.

### Motion

Every movement below is decorative and follows the system: when the system's animations are off (on Android, `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, which "Remove animations" sets; on iOS, Reduce Motion), nothing moves. Cloths open and close at once, the drips hang still at the hem, there's no shaking or spray, and the rain is drawn still. Read through one small `expect` function.

- **The shake.** Choosing a day, or tapping the open cloth again, shakes it. It leans from its top edge (a skew about the top edge, not a rotation), so the top stays flat along the line with its pegs and the hem swings. The wetter the day, the harder:

  | Verdict | Largest lean | Swings | Length | Drops thrown |
  |---|---|---|---|---|
  | `WILL_DRY` | 4° | 3 | 0.9 s | none |
  | `PARTLY_DRY` | 6° | 5 | 0.8 s | 12 |
  | `DONT_BOTHER` | 9° | 6 | 0.75 s | 26 |

- **The spray.** On a wet cloth's shake, 120 ms in, drops leave from both side edges of the cloth, placed along its height, arc outward and up, then fall 50 to 130 dp and fade, over 0.6 to 1 s each.
- **The rain**, on a `DONT_BOTHER` day with a rain line: thin white streaks at a slope of 1 across to 4 down, falling along that slope, in two layers. The near streaks fall at about 320 dp/s, the far ones at half that, the whole at 38 % opacity, behind the place and the cloths. It fades in and out over 500 ms when the day changes.
- **The drips** and the cloth opening, as set out above.

### Other screens

First run, search and places keep their layouts. They take the palette, Public Sans for text and Big Shoulders for their titles, and the square 2 dp-bordered buttons, so the app reads as one thing. No sky or line on them.

### Text

In string resources. The verdicts are shorter, drafted with the maintainer's voice profile (an instruction, not a description); the maintainer can still change any of them in PR 3:

| Key | Was | Now |
|---|---|---|
| `verdict_will_dry` | Will dry | Will dry |
| `verdict_partly_dry` | Partly dry, finish indoors | Finish indoors |
| `verdict_dont_bother` | Don't bother, dry indoors | Dry indoors |
| `verdict_day_over` | Too late to dry outside today | Too late today |
| Small cloth names | (none) | Mon, Tue, Wed, Thu, Fri, Sat, Sun |
| Cloth, spoken | (none) | %1$s, %2$s (the day and its verdict) |
| `window_note_open` | The bar runs from when the washing starts to dry … | The washing runs from when it starts to dry until it's dry, or until damp air, rain or sunset would start to undo the work. Hang it out at the start and bring it in by the end. |

The short verdicts the tabs used (`verdict_short_*`) go, with the tabs.

### What goes

The day tabs, the verdict pictures beside the headline and in the tabs (`verdict_*` in `art/` and the drawables), their mapping and its test, and bolt 8's bar. Removing a test is a protected change no spec covers, so the PR that removes them (PR 5) is high, as it would be anyway. The condition pictures in the hours row and the load weights stay.

### Release

In `androidApp/build.gradle.kts`, `versionName` 1.3.0 and `versionCode` 5, with `fastlane/metadata/android/en-US/changelogs/5.txt`. The four store screenshots are retaken from the final main. After the PR is merged, the tag goes on, and the maintainer runs `scripts/release.py 1.3.0`, as `docs/releasing.md` sets out.

### Network, permissions, dependencies

- **Network:** no change. The fonts are bundled; nothing is fetched.
- **Permissions:** none new, and no manifest change.
- **Dependencies:** none new and no catalogue change. The fonts are resources, not dependencies.
- **Stored data:** no change.

## Out of scope

- Any change to the engine, its rules, thresholds or verdicts, or to the forecast request and cache.
- Moving Refresh into the sky (the mock shows it there as a placeholder).
- Weather in the sky beyond the verdict's picture and the rain: no wind, temperature or hour-by-hour sky.
- Garments chosen by the load (heavy loads hanging towels and jeans). A possible follow-up, not this bolt.
- The items in `docs/PLAN.md`'s "Later". Translations, notifications and widgets.

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- Font tests: each of the five fonts loads, and each family's licence file is present in the resources.
- Contrast tests: every text and background pair in the palette, light and dark, including the cloth ink on the darkest wet patch and each sky's ink on its sky, is at least 4.5:1.
- Wet patch tests: its share is `100 − dryness` for each verdict, 0 for will dry, none for a `null` day.
- Washing row tests: the same seed and width give the same garments; the first starts at the span's start and the last ends at its end; gaps are at least 4 dp; no garment is cut off; a span narrower than a sock gives one centred sock kept inside the row; a span of zero (the times meeting) gives the same.
- Cloth label tests: the short and full names for each day, the spoken label with the verdict, a `null` day's label.
- Motion test: with animations off, the logic that picks the shake, spray count and drips gives none.
- The bolt 8 placement, opening and spoken-text tests pass unchanged.
- No real places in tests, fixtures or screenshots, beyond the reference set's.
- On the emulator (API 36), with a debug build. Recorded in PR 5 with screenshots and short screen recordings:
  - a will-dry, a partly-dry and a don't-bother day, in light and dark, with the rain on the don't-bother day;
  - choosing each day, the cloth opening without its text re-wrapping, and the shake and spray at each strength; tapping the open cloth again;
  - "Remove animations" on: no movement anywhere, drips hanging still;
  - the washing row on a long and a short window, and the one-label times;
  - a `null` day, the loading, failed and out-of-date states;
  - first run, search and places in the new type and colours;
  - 200 % font scale with no clipping, the cloths hung one under another;
  - TalkBack reading a cloth ("Tomorrow, Finish indoors, tab, selected"), the open verdict as a heading, and the washing row's sentence.

  As in bolts 7 and 8, live data gives whatever the day brings, so the PR says which verdict was live and shows the others with fixtures through an uncommitted fake source.
- PR 6: `versionCode` 5 and `versionName` 1.3.0, `changelogs/5.txt` within F-Droid's length. `v1.3.0` is on GitHub Releases with the signed APK and its SHA-256.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec and its mock, the lines it changes in bolts 5, 7 and 8, the new verdict wording in `docs/PLAN.md`, and bolt 9 in its table | high (a visual design) |
| 1 | The fonts and their licences, the type styles, the font test | medium (the files, sources and commits this spec names) |
| 2 | The palette and its contrast test, applied to first run, search and places | medium (UI) |
| 3 | The logic and the text: wet share, cloth labels, the washing row, the motion choices, the new strings, with tests | medium (UI copy) |
| 4 | The drawings: garments, the drop, the sky pictures, as SVG sources in `art/` and vector drawables, and the contact sheet | medium (art for the design this spec approves; the maintainer sees it on screen in PR 5) |
| 5 | The main screen: the sky, the line and cloths, the washing row, the motion, the restyled sections; removing the tabs, verdict pictures and bar; the emulator run | high (asks the maintainer, from the recordings: "Does the line read at a glance, and is the motion right?") |
| 6 | 1.3.0: version, changelog, retaken screenshots. After it's merged: the tag, then `scripts/release.py 1.3.0` | high (version bump, store listing) |
