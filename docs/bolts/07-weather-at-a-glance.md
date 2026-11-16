# Bolt 7: weather at a glance

The main screen shows the weather it worked from. After v1.0 the maintainer found the screen too wordy and missed the forecast itself: the app fetches it but never shows it. This bolt adds three things: a picture for each verdict, a row of hours for the selected day (sky, temperature, chance of rain, with the drying window marked) and one line saying when rain is due. The verdict stays first and the advice doesn't change: no change to the engine's rules or thresholds.

It reverses one line of bolt 5's "Out of scope" ("Hourly charts or the raw weather"), as the maintainer asked on 2026-11-14.

## In scope

### The sky

Open-Meteo's `weather_code`, a WMO code, gives the sky. The engine doesn't need it, since radiation already stands in for cloud, but the screen does.

**The request.** The forecast request's `hourly` list gains `weather_code`, at the end:
`temperature_2m,relative_humidity_2m,wind_speed_10m,visibility,precipitation_probability,precipitation,wind_gusts_10m,shortwave_radiation,weather_code`. Nothing else changes: the same host (`api.open-meteo.com`), the same rounded coordinates and the same other parameters. Checked on 2026-11-15 at the Greenwich landmark: `hourly_units.weather_code` is `"wmo code"`, with an integer for each hour. Open-Meteo's docs list the hourly value as "Instant".

**`Condition`**, in `engine` next to `HourlyWeather`, in this order, which is also the order of severity:

| `Condition` | WMO codes |
|---|---|
| `CLEAR` | 0, 1 |
| `PARTLY_CLOUDY` | 2 |
| `CLOUDY` | 3 |
| `FOG` | 45, 48 |
| `DRIZZLE` | 51, 53, 55, 56, 57 |
| `RAIN` | 61, 63, 65, 66, 67 |
| `SHOWERS` | 80, 81, 82 |
| `SNOW` | 71, 73, 75, 77, 85, 86 |
| `THUNDER` | 95, 96, 99 |

Any other code is no condition (`null`).

**`HourlyWeather` gains `condition: Condition?`**, defaulting to `null`, documented as something the engine doesn't read. The value is instantaneous, so the mapping can't average it the way it does temperature. The hour from `start` takes the more severe of the conditions at `start` and `start + 1 h`. If one is `null` it takes the other, and if both are, `null`. The field is optional, unlike the engine's inputs:

| In the response | Result |
|---|---|
| `weather_code` missing or `null` for a stamp, or an unknown code | `null` for that stamp. The hour is kept |
| The `weather_code` array missing | Every hour's condition is `null`. Not `BAD_RESPONSE` |
| A `weather_code` array of a different length from `time` | `BAD_RESPONSE`, as with any other hourly array |

**The cache.** `ForecastCache` keeps `condition` with each hour, by its enum name, under version 2. A stored name it doesn't know is read as `null` for that hour, and the entry is kept. So the private stored type holds it as a `String?` mapped through `Condition.entries`, not as a serializable enum, which would fail the whole entry. A version 1 entry is still read, with every condition `null`, so the first launch after the update still has its forecast offline. The next write (a `put`, or a `retain` that drops something) stores version 2. Any other version is still no cache. This PR edits bolt 5's "What's kept" to read "every field the engine or the screen reads, and nothing else", and takes "Hourly charts or the raw weather" out of its "Out of scope".

### When rain is due

A public function in `engine`, `firstWet(forecast, date, now): Instant?`, gives the start of the usable part of the first `WET` hour (rule 2) in the day's usable time (rule 1): daylight, and from `now` on today. It returns `null` when there's none. It also returns `null` for a date outside `today` to `today + 2`, and for a day the engine gives `null` advice for. It decides that with the same completeness check `advise` uses (shared, not copied), so the rain line and the verdict can't disagree on whether a day has data. It uses the classification `advise` already uses (the internal `HourKind.WET`), so it adds no rule and no threshold: an hour the line counts as wet is the hour the screen calls wet.

It isn't the `RAIN` reason. `RAIN` counts only a wet hour that ends the chosen window or comes before it, and a `WILL_DRY` day has no reasons. `firstWet` gives the first wet hour of the day wherever it falls, so a `WILL_DRY` day with rain after bring-in says so: "Bring in by 14:30" followed by "Rain likely from 16:00" is meant. It tells people why the bring-in time matters.

### The screen

Changes to bolt 5's main screen, in its order:

1. **The days.** Each tab gains its verdict's picture, small, before the short verdict.
2. **The selected day:**
   - **The verdict** gains its picture, large, before the headline.
   - **The rain line**, new, straight after the times, or after the headline on a `DONT_BOTHER` day: "Rain likely from 15:00." from `firstWet`, or "No rain expected while it's light." when it gives `null`. It's left out on a `DAY_OVER` day, on a `null` day, and on a day with no usable time (polar night), where "while it's light" would mean nothing. The time is rounded down to the minute and formatted the way bolt 5 formats times, in the forecast's zone.
   - **The hours**, new, after the rain line and before Why, Tips and Indoors. There's one column for each forecast hour that overlaps the day's daylight as the engine clips it (`DailyLight.daylight(zone)`): `start < daylight.end` and `start + 1 h > daylight.start`. Today, the hour must also end after `now`. So polar day runs midnight to midnight, and a sunset exactly on the hour adds no empty column. A gap in the forecast leaves no column. Each column holds, from the top:
     - the hour's start, in the forecast's zone and the device's time format, through bolt 5's `formatTime`. On the day the clocks go back, the repeated hour shows the same label twice, in order. On the day they go forward, the skipped label doesn't appear;
     - the hour's `condition` as a picture, or nothing for `null`;
     - the temperature, rounded half up, as "11°";
     - the chance of rain, as "40 %", when it's at least 10 %. It's left blank under 10 % or when the value is `null`.

     Columns that overlap `hangOut` to `bringIn` have a bar under them: the drying window. The row scrolls sideways, and today's opens at its first column. A `null` day, polar night or today after sunset shows no row.
3. Everything else is as bolt 5 left it.

The rules from bolt 5's "Design system" still hold: foundation only, at least 48 dp for anything tappable (the row isn't tappable), light and dark, and nothing carried by colour alone. At 200 % font scale the columns grow taller and wider without clipping.

**Accessibility.** The pictures are decorative, with their semantics cleared, as the verdict is always written out next to them. Each column is one node that reads "10:00, partly cloudy, 11 degrees, 40 % chance of rain, in the drying window". It leaves out the parts the column doesn't show. It's built by joining, with ", ", the parts that apply, each its own string resource. The time comes from the same `formatTime` as the label. The bar is described only through that node.

### The pictures

Drawn for this app and licensed with it (GPL-3.0-or-later). No icon set and no new dependency. Each has an SVG source in `art/` next to the app icon's. The app uses them as vector drawables in `shared/src/commonMain/composeResources/drawable/`, through `painterResource`, which the resources library the app already uses provides.

- **Nine conditions**, one per `Condition`, on a 24 dp grid. They're line drawings in one colour, tinted with the text colour, so they follow light and dark mode without a second set.
- **Three verdicts**, each one t-shirt with the outcome drawn on it, so a glance reads the result, not the method:
  - `WILL_DRY`: a sparkle.
  - `PARTLY_DRY`: one drop.
  - `DONT_BOTHER`: three drops.

  These are in the same style, with thinner strokes than the sky (0.8 against 1.5 on the 24 dp grid), as they're drawn at 48 dp beside the headline as well as at 24 dp in the tabs. The maintainer chose this design in review of PR 2, in place of a washing line, a house and an airer.
- Exhaustive `when`s map each `Condition` to its drawable and its spoken name, and each `Verdict` to its drawable, so a new code doesn't compile without them. A verdict picture has no spoken name, as the written verdict beside it is what's read.

PR 2 includes a contact sheet of all twelve in light and dark. That's the maintainer's first look at the style, and the place to change it before the screen is built. So PR 2 is high: it waits for the maintainer's approval, which answers "is this the style?". PR 2 also carries the spoken names' string resources, so its mappings compile on their own.

### Text

In string resources, the maintainer's voice, as with the copy in PR 54. To start from:

| Key | Text |
|---|---|
| Rain line | Rain likely from %1$s. / No rain expected while it's light. |
| Conditions (spoken) | clear, partly cloudy, cloudy, fog, drizzle, rain, showers, snow, thunder |
| Column parts (spoken) | %1$s degrees / %1$s chance of rain / in the drying window, with the time and condition name as they are |

As with `forecast_dryness`, the percentage is passed in already formatted ("40 %"), so no string holds a literal `%`.

### Release

In `androidApp/build.gradle.kts`, `versionName` 1.1.0 and `versionCode` 3, with `fastlane/metadata/android/en-US/changelogs/3.txt`. The four store screenshots are retaken from the final main, as in PR 63. `fastlane/.../full_description.txt` gains one sentence about the hourly view. After the PR is merged, the tag goes on, and then the maintainer runs `scripts/release.py 1.1.0`, as `docs/releasing.md` sets out.

### Network, permissions, dependencies

- **Network:** only the `weather_code` added to the forecast request above. No new host and no new data about the user: the coordinates are rounded as before.
- **Permissions:** none new, and no manifest change.
- **Dependencies:** none new and no catalogue change. `painterResource` is in `org.jetbrains.compose.components:components-resources`, already in use. No verification metadata change is expected; PR 2 records whether there was one.

## Out of scope

- Any change to the engine's rules, thresholds or verdicts. `Condition` is never an input to `advise`.
- Wind, humidity or radiation on screen.
- A daily summary picture in the tabs (the verdict picture stands in).
- Night hours.
- Pictures of how to hang clothes, a settings page for equipment (airer, dehumidifier), and a Breezy-style place switcher. Each is a later bolt, listed in `docs/PLAN.md`.
- Translations, notifications and widgets.

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- Mapping tests:
  - every code in the table, an unknown code, `null` and a missing array;
  - the more severe of two stamps, and one stamp `null`;
  - a `weather_code` array of a different length giving `BAD_RESPONSE`;
  - the request's `hourly` list exactly as above.

  The response fixtures in `ForecastMappingTest` and `OpenMeteoForecastTest` gain `weather_code`, and one keeps a response without it.
- An engine test over the whole reference set: `advise` gives identical `DayAdvice` with every condition `null`, as the generated forecasts have them, and with synthetic conditions filled in by the test, each hour cycling through `Condition.entries`. The reference set's labels, fixtures, generated code and fetch script don't change.
- `firstWet` tests:
  - a wet first hour;
  - rain only after sunset;
  - today with `now` partway through a wet hour, and with a wet hour that ended before `now`;
  - a date outside `today` to `today + 2`;
  - a dry day;
  - polar night;
  - a `null` day;
  - a wet hour at a daylight-saving change.

  Each test checks the result against the hours the internal `kind()` classifies as `HourKind.WET`, not against `RAIN` in reasons. One test is a `WILL_DRY` day with rain after bring-in.
- Cache tests:
  - a version 2 round trip with conditions;
  - a version 1 entry read with `null` conditions and rewritten as version 2;
  - an unknown stored condition read as `null`, keeping the entry.

  The stored-keys test is updated to include `condition`. `anotherVersionIsNoCache` changes its other version from 2 to 3 and keeps its assertion that the entry is dropped.
- Screen tests:
  - which hours get a column (sunrise and sunset mid-hour, a sunset on the hour, polar day, today from `now`, a gap, polar night, today after sunset, and both daylight-saving changes);
  - which columns carry the bar (partial overlap at each end);
  - the rain chance shown from 10 %, blank under it and for `null`;
  - temperature rounding at .5 and at −0.5;
  - the spoken text of a column with and without each part;
  - the rain line's two texts, its time rounded down to the minute and shown in the forecast's zone at a zone different from the device's, and the line left out on `DAY_OVER`, `null` and polar-night days;
  - every `Condition` having a drawable and a spoken name, and every `Verdict` a drawable, through the exhaustive mappings.
- No real places in tests, fixtures or screenshots, beyond the reference set's.
- On the emulator (API 36), with the release APK as bolt 6 requires, since it adds JSON decoding (`weather_code`, the stored condition, the version 1 read). Recorded in PR 4 with screenshots:
  - a will-dry, a partly-dry and a don't-bother day, in light and dark;
  - the hours scrolled;
  - today after the hang-out time;
  - 200 % font scale with no clipping;
  - TalkBack reading a column;
  - offline after updating over a build from `main` before PR 1 that has cached a version 1 forecast, showing the forecast with no pictures in the row. The installed 1.0.1 is signed with the maintainer's key, so the agent can't update over it. The run uses two debug builds instead, then a fresh install of the release APK for the decoding paths, signed with a throwaway key the way `scripts/release.py --dry-run` does.
- PR 5: `versionCode` 3 and `versionName` 1.1.0, `changelogs/3.txt` within F-Droid's length (the existing `scripts/` test), and the listing sentence. `v1.1.0` is on GitHub Releases with the signed APK and its SHA-256, as for 1.0.0.

  As in bolt 5, live data gives whatever the day brings, so the PR says which verdict was live and shows the others with fixtures through an uncommitted fake source.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec, bolt 5's two lines, and bolt 7 and the later ideas in `docs/PLAN.md` | high (a new request field, a cache format change, a visual design) |
| 1 | `Condition`, `HourlyWeather.condition`, the request field, the mapping and its fixtures, cache version 2, `firstWet`, with tests | medium (the request change and cache format this spec names; the two cache test edits above are named here and weaken nothing) |
| 2 | The twelve pictures: SVG sources, vector drawables, the mappings, the spoken names' strings and their tests, the contact sheet | high (the maintainer approves the style) |
| 3 | The text and the screen logic: which hours, the bar, the rain line, the spoken text, with tests. After PR 2, whose condition names it uses | medium (UI copy, time display) |
| 4 | The screen: the pictures in the tabs and the headline, the rain line, the hours row, the emulator run | high (asks the maintainer, from the screenshots: "Does the screen read at a glance, with the verdict still first?") |
| 5 | 1.1.0: version, changelog, retaken screenshots, the listing sentence. After it's merged: the tag, then `scripts/release.py 1.1.0` | high (version bump, store listing) |
