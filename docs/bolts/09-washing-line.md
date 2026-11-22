# Bolt 9: the washing line

The maintainer wasn't happy with how the app looks: it's plain foundation components in the system font, with nothing in it of washing or weather. This bolt gives the main screen one idea, the washing line, and a look the other screens share. Each day is a cloth pegged on a line, and the chosen day hangs large with its verdict printed on it. The sky behind the line is the day's weather. The drying window becomes a row of washing pegged on a second line above the hours. The engine, its advice and the forecast don't change.

The design was settled with the maintainer in an interactive mock, kept next to this spec as [09-washing-line.html](09-washing-line.html). It shows three phones in light and dark. Where the mock and this spec differ, this spec wins. The mock is a design reference only and ships nowhere: opening it loads the two fonts from Google Fonts, which the app never does.

## In scope

### Type

Two typefaces, bundled with the app, both under the SIL Open Font License 1.1 with no Reserved Font Name:

| Face | Used for | Source |
|---|---|---|
| Big Shoulders Display (Patric King, Xotype) | The verdict on the open cloth, the day names on the cloths, the section headings | `github.com/xotypeco/big_shoulders` at commit `41153e6fe01d218e933919a1d08c8e45065bc8fe` |
| Public Sans (USWDS) | Everything else: sentences, tips, times, buttons, labels | `github.com/uswds/public-sans` at commit `c7923167a592d941646f99fb7b5fba17aa7d69e1` |

The commits are the ones Google Fonts builds each family from (`ofl/bigshouldersdisplay/METADATA.pb` and `ofl/publicsans/METADATA.pb` in `github.com/google/fonts`, read on 2026-11-22). Static files, not the variable ones, so nothing depends on variable-font support. The files, byte for byte as upstream, renamed in lower case with underscores so the resource accessors are plain:

| Upstream path | In the app (`shared/src/commonMain/composeResources/`) | Bytes | SHA-256 |
|---|---|---|---|
| `Big-Shoulders/fonts/ttf/static/BigShouldersDisplay-Bold.ttf` | `font/big_shoulders_display_bold.ttf` | 89,444 | `8bbf6cc1a50aa41d475294ca88d111869c928d432df5d3f49ae0c037155d0bb5` |
| `Big-Shoulders/fonts/ttf/static/BigShouldersDisplay-ExtraBold.ttf` | `font/big_shoulders_display_extrabold.ttf` | 89,852 | `ca975779c90d08a721fee703cce970a6426a41a4bcb1d51b01e146456ce20389` |
| `OFL.txt` (Big Shoulders repo root) | `files/licenses/ofl_big_shoulders.txt` | 4,396 | `338f9c050f19daeda1d597243faf79f3a3d437c338af58cb7047617d0ce08771` |
| `fonts/ttf/PublicSans-Regular.ttf` | `font/public_sans_regular.ttf` | 84,836 | `b577e9bc9887284e90aae5ad0699689ce36b5cd96207efbec68f77f8aed88379` |
| `fonts/ttf/PublicSans-SemiBold.ttf` | `font/public_sans_semibold.ttf` | 84,412 | `9f537d607dc78450841dc31c401e3c4ba7a0ba7217e8b34c1be684a983806399` |
| `fonts/ttf/PublicSans-Bold.ttf` | `font/public_sans_bold.ttf` | 84,744 | `bddd96a0bcd49b292524b4beff57d770ce97482fb3f84b788959422e06b863a6` |
| `OFL.txt` (Public Sans repo root) | `files/licenses/ofl_public_sans.txt` | 4,390 | `157a9e77f7580246e97c769490e2e977ae94399f9d30f4556015c41fe8c28bac` |

- The fonts are used through `Font(Res.font.…)` from the resources library the app already uses. No new dependency and no build script change. A PR adding them that touches `gradle/`, a Gradle script or a manifest is high.
- The licence texts ship in the APK with the fonts, as the OFL asks. `README.md`'s Licence section names the two fonts, their licence and those files. `LICENSE` is untouched. The fonts stay under the OFL; the app stays GPL-3.0-or-later. The OFL allows bundling with any software, and F-Droid accepts OFL fonts.
- PR 1 checks each file's SHA-256 against this table.

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

A `null` day has the `PARTLY_DRY` sky with no picture in it. Every pair of text and its background is at least 4.5:1, and PR 2 records each ratio in a test that fails if one drops below. Text never sits over the sky's picture or rain: both are drawn only below the place's row (below), so the place is always on the plain sky colour. PR 2 leaves the main screen's colours alone; PR 5 moves it onto the palette.

### The main screen

From the top:

1. **The sky.** The full width of the screen, from the top edge to just below the cloths, in the selected day's sky colour. Behind the line and the cloths, and clipped to the sky below the place's row, one picture by verdict, drawn on the right:
   - `WILL_DRY`: the sun, with a soft ring;
   - `PARTLY_DRY`: the sun half behind a cloud;
   - `DONT_BOTHER` (and today when its only reason is `DAY_OVER`): a dark cloud across the top. Rain falls, as set out under Motion, only on a `DONT_BOTHER` day whose `firstWet` (bolt 7) isn't `null`, the day that reads "Rain likely from 09:32". A fog or cold day reading "No rain expected while it's light", today after sunset and a `PARTLY_DRY` day get no rain.

   The verdict to sky, ink and picture is one exhaustive `when`, with a `null` day giving the `PARTLY_DRY` sky and no picture. Changing day crossfades the sky's colour over 400 ms and swaps the picture.

   In the sky, from the top: **the place**, as now (its name, tappable, opening the places screen), in the sky's ink; then **the line** with the three cloths.
2. **The load toggle and its note**, under the sky, unchanged from bolt 8 except that the halves have square corners and a 2 dp ink border, the selected half filled with ink and its text in the page colour (with its border and bold text still, so not colour alone). (Bolt 8 put the toggle above the days. Here the line and the sky read as one picture, so the toggle goes straight under it, still above everything the load changes on the screen except the cloths.)
3. **The washing and the hours**: the hours row from bolt 7, with the row of washing above it in place of bolt 8's bar (below). Then the time-zone line and the drying window note, as bolt 8 has them. Bolt 8's fallback stays: if a day with a window ever had no hours row, the times are written out.
4. **Why, Tips and Indoors**, as now, restyled: the heading in Big Shoulders, each line led by a small peg in peg wood (6 by 14 dp) in place of a bullet, and the lines divided by a dashed rule, like stitching. No cards and no boxes.
5. **The forecast's status, Refresh and the credit**, as now. Refresh gets the toggle's square border.

Without a usable forecast: the sky in the `PARTLY_DRY` colour with no picture, the place, the empty line, then the load toggle and its note, the message for the status, and the credit.

### The line and the cloths

The line is a 2 dp rule across the sky's full width in the sky's ink at 80 %. Three cloths hang from it, left to right Today, Tomorrow, the day after, 8 dp apart.

**A small cloth**, for a day that isn't selected: 62 dp wide, its name at the top, its wet patch and its drips. The name is short so it fits: "Today", then the weekday's three-letter name ("Mon", "Tue") for the other two. **The open cloth**, the selected day: the rest of the width. From the top:

- the day's full name: "Today", "Tomorrow", or the weekday ("Tuesday");
- the verdict (below, under Text), as a heading;
- the sentence: on a `PARTLY_DRY` day the dryness line, then the rain line; otherwise the rain line, as bolt 7 and bolt 8 have them. Today after sunset (`DAY_OVER` its only reason) and polar night have no rain line, so no sentence. A `null` day: "There isn't enough forecast for this day yet." (`forecast_day_missing`, as now) and nothing else;
- a dashed hem 12 dp above the bottom edge.

**One height.** All three cloths are the same height: the tallest of the three days' open content at the open cloth's final width, and at least 120 dp. So the line doesn't change height when the day changes, nothing below it moves, and a small cloth's wet patch is measured against the same height as the open one's. Worked out in a custom layout, again when the width, font scale, load or forecast changes. A load switch or a refresh can change the height, and what's below moves then; that's accepted, as both are the user's own action or a new forecast.

**Every cloth** is drawn, not an image: the cloth colour with a faint weave (1 dp lines every 3 dp both ways, black at 3 %), a hard shadow 3 dp right and 4 dp down (black at 24 %), and two pegs, 8 by 19 dp, at its top corners, clipping it to the line.

**The wet patch** is how much of the day's washing is still wet when it comes in: its height is `100 − dryness` % of the cloth's height, measured up from the bottom edge. So a will-dry cloth is plain, a 76 %-dry cloth is damp in its bottom 24 %, and a don't-bother cloth is mostly or all wet. The patch is `#1C3A5C`, from 40 % at the bottom edge to 16 % at its top, with a gently wavy top edge. The text on a fully wet cloth keeps 4.5:1 against the patch's darkest point, and PR 2's contrast test includes it.

**No patch and no drips** on a day the washing was never out to get wet: a `null` day, today when `DAY_OVER` is its only reason, and a day with no usable time (polar night, by the same test bolt 7 uses to leave out the rain line). The engine gives those a `dryness` of 0, and a soaked cloth would say the wrong thing. PR 3 makes "the washing was never out" one internal predicate on the forecast, date, advice and `now`, in place of the inline tests in `rainLine` and the screen, and uses it for the rain line, the patch, the drips, the motion and the sky. Their cloths are plain, and choosing one gives the dry swing with no spray.

**The drips** hang from the bottom edge of a wet cloth and fall: on a `PARTLY_DRY` cloth, one when small and three when open; on a `DONT_BOTHER` cloth, three when small and eight when open. A will-dry cloth doesn't drip. Each is a drop 9 by 13 dp that swells at the hem, falls about 74 dp and fades, on its own loop of 1.8 to 3.6 s, so they don't fall together.

**Choosing a day.** The whole cloth is the target, at least 48 dp each way. Tapping a small cloth opens it and closes the open one: the widths change over 400 ms. The open cloth's text is laid out at its final width from the start and revealed as the cloth widens, fading in over 300 ms after 180 ms, so its lines never re-wrap while it moves. Tapping the open cloth again just moves it (below). The selection rules are bolt 5's: today on launch and when `Showing.today` changes, kept across rotation.

**Stacked.** The cloths hang one under another, each the full width with its own line above it, in the same order, when the font scale is 1.5 or more (where the day tabs stack now), or when the open cloth's width can't hold the longest word of any of the four verdicts at its size, measured. The decision is pure logic taking the font scale, the open cloth's width and the measured word's width as numbers, so it's unit-tested; the measuring is checked on the emulator. So a narrow phone at 1.4 stacks rather than break "indoors" mid-word. The open one shows everything above; the others their full name, wet patch and drips, at their own height. Nothing clips at 200 %, and a verdict wraps only between words.

**Accessibility.** The line is a `selectableGroup()`. Each cloth's tap target is one node with `Role.Tab` and its selected state, labelled with the day's full name and verdict, "Tomorrow, Finish indoors" (a `null` day: the name alone), its own text merged into it. On the open cloth, the verdict (a heading) and the sentence are separate nodes, not merged into the tab, read after the three tabs. The weave, pegs, wet patch, drips, sky picture and rain are decorative, their semantics cleared. The wet patch and drips repeat what the written verdict says, so nothing is carried by them alone.

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

**Which garments**, as pure logic: a function from the span's width in dp and a seed (the day's date) to a list of garments and their x positions. It picks garments and colours with a fixed pseudo-random sequence from the seed, adding them while they fit with at least 4 dp between them. Two or more are spread so the first starts at the span's start and the last ends at its end. So a day always hangs the same washing, the row fills its window, and nothing is cut off. When only one fits, it's centred on the span. A span narrower than the narrowest garment (the sock), including a span of zero when the times meet, hangs one sock centred on the span. Either way a lone garment is kept inside the row, so a short window still shows. The row is worked out again when its width changes (font scale, rotation).

The space above the hours grows to fit the trousers, the tallest garment, with the times above the line.

### Motion

Every movement below is decorative and follows the system: when the system's animations are off (on Android, `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, which "Remove animations" sets; on iOS, Reduce Motion), nothing moves. Cloths open and close at once, the sky and the rain change at once, the text appears at once, the drips hang still at the hem, there's no shaking or spray, and the rain is drawn still. The setting is read through one small `expect` function that's observed, not read once (on Android a `ContentObserver` on that setting; on iOS the Reduce Motion notification), so changing it while the app is open applies.

The lean is a skew, which `graphicsLayer` doesn't offer: it's drawn with a draw-phase transform (`withTransform` and a `Matrix` with the skew set) about the cloth's top edge.

What each verdict gets (shake, spray count, drips) is pure logic in PR 3, from the verdict, whether the day has a patch (above) and whether animations are on, so it's tested without drawing.

- **The shake.** Choosing a day, or tapping the open cloth again, shakes it. It leans from its top edge (a skew about the top edge, not a rotation), so the top stays flat along the line with its pegs and the hem swings. The wetter the day, the harder:

  | Verdict | Largest lean | Swings | Length | Drops thrown |
  |---|---|---|---|---|
  | `WILL_DRY` | 4° | 3 | 0.9 s | none |
  | `PARTLY_DRY` | 6° | 5 | 0.8 s | 12 |
  | `DONT_BOTHER` | 9° | 6 | 0.75 s | 26 |
  | No patch (`null`, day over, polar night) | 4° | 3 | 0.9 s | none |

- **The spray.** On a wet cloth's shake, 120 ms in, drops leave from both side edges of the cloth, placed along its height, arc outward and up, then fall 50 to 130 dp and fade, over 0.6 to 1 s each.
- **The rain**, on a `DONT_BOTHER` day whose `firstWet` isn't `null`: thin white streaks at a slope of 1 across to 4 down, falling along that slope, in two layers. The near streaks fall at about 320 dp/s, the far ones at half that, the whole at 38 % opacity, behind the place and the cloths. It fades in and out over 500 ms when the day changes.
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
| `forecast_day_missing` | There isn't enough forecast for this day yet. | unchanged, on a `null` day's open cloth |

PR 3 changes the strings and updates the existing tests that expect the old verdict text (`ForecastTextTest`) to expect the new. That changes what's expected, not how much is checked, so it isn't weakening a test. The short verdicts the tabs used (`verdict_short_*`) go with the tabs, in PR 5, as the tabs use them until then.

### What goes

The day tabs, the verdict pictures beside the headline and in the tabs (`verdict_*` in `art/` and the drawables), their mapping and its test, and bolt 8's bar. Removing a test is a protected change no spec covers, so the PR that removes them (PR 5) is high, as it would be anyway. The acceptance criteria and emulator checks in bolts 5, 7 and 8 that are about those (the tabs, the verdict pictures, the bar) retire with them; this spec's criteria take their place. The condition pictures in the hours row and the load weights stay.

### The drawings

Drawn for this app and licensed with it (GPL-3.0-or-later): the garments (`garment_tee`, `garment_trousers`, `garment_shorts`, `garment_sock`, `garment_towel`), the `drop`, and the sky pictures (`sky_sun`, `sky_sun_cloud`, `sky_rain_cloud`). Each has an SVG source in `art/`. Each needs more than one colour (a garment's fill and its outline, the sun, its ring and the cloud), and a vector drawable can only be tinted as a whole, so the app draws them in code: their path data sits in one Kotlin file, parsed with `PathParser` from Compose's graphics library (already in the app), each layer filled or stroked with its palette colour at draw time, so one set serves light and dark. A test checks each SVG in `art/` against its path data. No new drawables. The cloths, pegs, weave, wet patch and rain are drawn in code too. PR 4 adds the drawings to the contact sheet (`art/pictures-sheet.png`), in light and dark.

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
- Wet patch tests: its share is `100 − dryness` for each verdict, 0 for will dry; none for a `null` day, today after sunset and polar night.
- Sky tests: each verdict's sky, ink and picture, a `null` day's, today after sunset's; rain only for a `DONT_BOTHER` day with `firstWet`, not for a fog day, a `PARTLY_DRY` day with rain coming, or today after sunset.
- Washing row tests: the same seed and width give the same garments; with two or more, the first starts at the span's start and the last ends at its end; gaps are at least 4 dp; no garment is cut off; one garment that fits alone is centred; a span narrower than a sock, and a span of zero, give one centred sock; a lone garment at the row's edge is kept inside it.
- Cloth label tests: the short and full names for each day, the spoken label with the verdict, a `null` day's label, "Today, Too late today".
- Motion tests: the shake, spray count and drips for each verdict and each no-patch day, small and open, as the tables give them; with animations off, none.
- Stacking test: stacked from font scale 1.5; and below it when the measured longest verdict word doesn't fit the open cloth.
- Text tests: the new verdict strings, today after sunset's, and the new window note.
- The bolt 8 placement, opening and spoken-text tests pass unchanged.
- No real places in tests, fixtures or screenshots, beyond the reference set's.
- On the emulator (API 36), with a debug build. Recorded in PR 5 with screenshots and short screen recordings:
  - a will-dry, a partly-dry and a don't-bother day, in light and dark, with the rain on the don't-bother day;
  - choosing each day, the cloth opening without its text re-wrapping, and the shake and spray at each strength; tapping the open cloth again;
  - "Remove animations" on: no movement anywhere, drips hanging still;
  - the washing row on a long and a short window, and the one-label times;
  - a `null` day, the loading, failed and out-of-date states;
  - first run, search and places in the new type and colours;
  - the cloths keeping one height as the day changes, with nothing below them moving;
  - today after sunset: a plain cloth, the dark sky with no rain, no sentence;
  - 200 % font scale with no clipping, the cloths hung one under another; and 1.4 on a 320 dp-wide emulator, stacked or not, with no word broken;
  - TalkBack reading a cloth ("Tomorrow, Finish indoors, tab, selected"), the open verdict as a heading, and the washing row's sentence.

  As in bolts 7 and 8, live data gives whatever the day brings, so the PR says which verdict was live and shows the others with fixtures through an uncommitted fake source.
- PR 6: `versionCode` 5 and `versionName` 1.3.0, `changelogs/5.txt` within F-Droid's length. `v1.3.0` is on GitHub Releases with the signed APK and its SHA-256.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec and its mock, the lines it changes in bolts 5, 7 and 8, the new verdict wording in `docs/PLAN.md`, and bolt 9 in its table | high (a visual design) |
| 1 | The fonts and their licences as the table names them, the type styles, the README's Licence lines, the font test | medium (the files, sources, commits and hashes this spec names) |
| 2 | The palette and its contrast test, with the fonts and square buttons, applied to first run, search and places; the main screen untouched | medium (UI) |
| 3 | The logic and the text: the never-out predicate, wet share, the sky choice, cloth labels, stacking, the washing row, the motion choices, the new strings and the updated string tests | medium (UI copy) |
| 4 | The drawings this spec names: SVG sources in `art/`, their path data and its test, and the contact sheet | medium (art for the design this spec approves; the maintainer sees it on screen in PR 5) |
| 5 | The main screen: the palette, the sky, the line and cloths, the washing row, the motion, the restyled sections; removing the tabs, `verdict_short_*`, the verdict pictures and their test, and the bar; the emulator run | high (asks the maintainer, from the recordings: "Does the line read at a glance, and is the motion right?") |
| 6 | 1.3.0: version, changelog, retaken screenshots. After it's merged: the tag, then `scripts/release.py 1.3.0` | high (version bump, store listing) |
