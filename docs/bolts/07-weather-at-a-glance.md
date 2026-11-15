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

**The cache.** `ForecastCache` keeps `condition` with each hour, by its enum name, under version 2. A stored name it doesn't know is read as `null` for that hour, and the entry is kept. A version 1 entry is still read, with every condition `null`, so the first launch after the update still has its forecast offline. The next `put` writes version 2. "What's kept" in bolt 5 now reads "every field the engine or the screen reads, and nothing else".

### When rain is due

A public function in `engine`, `firstWet(forecast, date, now): Instant?`, gives the start of the usable part of the first `WET` hour (rule 2) in the day's usable time (rule 1): daylight, and from `now` on today. It returns `null` when there's none, and when the day is one the engine gives `null` advice for. It uses the classification `advise` already uses, so it adds no rule and no threshold: an hour the line counts as wet is the hour the screen calls wet.

### The screen

Changes to bolt 5's main screen, in its order:

1. **The days.** Each tab gains its verdict's picture, small, before the short verdict.
2. **The selected day:**
   - **The verdict** gains its picture, large, before the headline.
   - **The rain line**, new, straight after the times, or after the headline on a `DONT_BOTHER` day: "Rain likely from 15:00." from `firstWet`, or "No rain expected while it's light." when it gives `null`. It's left out on a `DAY_OVER` day and on a `null` day. The time is rounded down to the minute and formatted the way bolt 5 formats times, in the forecast's zone.
   - **The hours**, new, after the rain line and before Why, Tips and Indoors. There's one column for each forecast hour whose start falls from the hour containing sunrise to the hour containing sunset. Today, it starts at the hour containing `now`. A gap in the forecast leaves no column. Each column holds, from the top:
     - the hour's start, in the device's time format, as bolt 5 formats times;
     - the hour's `condition` as a picture, or nothing for `null`;
     - the temperature, rounded half up, as "11°";
     - the chance of rain, as "40 %", when it's at least 10 %. It's left blank under 10 % or when the value is `null`.

     Columns that overlap `hangOut` to `bringIn` have a bar under them: the drying window. The row scrolls sideways, and today's opens at its first column. A `null` day, polar night or today after sunset shows no row.
3. Everything else is as bolt 5 left it.

The rules from bolt 5's "Design system" still hold: foundation only, at least 48 dp for anything tappable (the row isn't tappable), light and dark, and nothing carried by colour alone. At 200 % font scale the columns grow taller and wider without clipping.

**Accessibility.** The pictures are decorative, with their semantics cleared, as the verdict is always written out next to them. Each column is one node that reads "10:00, partly cloudy, 11 degrees, 40 % chance of rain, in the drying window", leaving out the parts the column doesn't show. The bar is described only through that node.

### The pictures

Drawn for this app and licensed with it (GPL-3.0-or-later). No icon set and no new dependency. Each has an SVG source in `art/` next to the app icon's. The app uses them as vector drawables in `shared/src/commonMain/composeResources/drawable/`, through `painterResource`, which the resources library the app already uses provides.

- **Nine conditions**, one per `Condition`, on a 24 dp grid. They're line drawings in one colour, tinted with the text colour, so they follow light and dark mode without a second set.
- **Three verdicts**:
  - `WILL_DRY`: washing on a line in the sun.
  - `PARTLY_DRY`: washing on a line, with a house.
  - `DONT_BOTHER`: an airer indoors.

  These are in the same style, drawn at 48 dp and 24 dp.
- An exhaustive `when` maps each `Condition` and each `Verdict` to its drawable and its spoken name, so a new code doesn't compile without both.

PR 2 includes a contact sheet of all twelve in light and dark. That's the maintainer's first look at the style, and the place to change it before the screen is built.

### Text

In string resources, the maintainer's voice, as with the copy in PR 54. To start from:

| Key | Text |
|---|---|
| Rain line | Rain likely from %1$s. / No rain expected while it's light. |
| Conditions (spoken) | clear, partly cloudy, cloudy, fog, drizzle, rain, showers, snow, thunder |
| Column (spoken) | %1$s, %2$s, %3$s degrees, %4$s %% chance of rain, in the drying window |

### Release

`versionName` 1.1.0, `versionCode` 3, `changelogs/3.txt`. The four store screenshots are retaken from the final main, as in PR 63. `fastlane/.../full_description.txt` gains one sentence about the hourly view. After the PR is merged, the tag goes on, and then the maintainer runs `scripts/release.py 1.1.0`, as `docs/releasing.md` sets out.

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

  The Open-Meteo fixtures gain `weather_code`, and one keeps a fixture without it.
- An engine test over the whole reference set: `advise` gives identical `DayAdvice` with every condition set to `null` and with conditions filled in. The reference set's labels and the fixtures it uses don't change.
- `firstWet` tests:
  - a wet first hour;
  - rain only after sunset;
  - today with `now` partway through a wet hour;
  - a dry day;
  - polar night;
  - a `null` day;
  - a wet hour at a daylight-saving change.

  Each wet hour matches the hour `advise` treats as wet, checked against `RAIN` in reasons on the same fixtures.
- Cache tests:
  - a version 2 round trip with conditions;
  - a version 1 entry read with `null` conditions and rewritten as version 2;
  - an unknown stored condition read as `null`, keeping the entry.

  The stored-keys test is updated to include `condition`.
- Screen tests:
  - which hours get a column (sunrise and sunset mid-hour, today from `now`, a gap, polar night, today after sunset);
  - which columns carry the bar (partial overlap at each end);
  - the rain chance shown from 10 %, blank under it and for `null`;
  - temperature rounding at .5 and at −0.5;
  - the spoken text of a column with and without each part;
  - the rain line left out on `DAY_OVER` and `null` days;
  - every `Condition` and `Verdict` having a drawable and a spoken name, through the exhaustive mappings.
- No real places in tests, fixtures or screenshots, beyond the reference set's.
- On the emulator (API 36), recorded in PR 4 with screenshots:
  - a will-dry, a partly-dry and a don't-bother day, in light and dark;
  - the hours scrolled;
  - today after the hang-out time;
  - 200 % font scale with no clipping;
  - TalkBack reading a column;
  - offline after installing over 1.0.1 with a cached version 1 forecast, showing the forecast with no pictures in the row.

  As in bolt 5, live data gives whatever the day brings, so the PR says which verdict was live and shows the others with fixtures through an uncommitted fake source.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec, and bolt 7 and the later ideas in `docs/PLAN.md` | high (a new request field, a cache format change, a visual design) |
| 1 | `Condition`, `HourlyWeather.condition`, the request field, the mapping and its fixtures, cache version 2, `firstWet`, with tests | medium (the request change and cache format this spec names) |
| 2 | The twelve pictures: SVG sources, vector drawables, the mappings and their tests, the contact sheet | low (art, no behaviour) |
| 3 | The text and the screen logic: which hours, the bar, the rain line, the spoken text, with tests | medium (UI copy, time display) |
| 4 | The screen: the pictures in the tabs and the headline, the rain line, the hours row, the emulator run | high (the maintainer asked to shape the visual design together) |
| 5 | 1.1.0: version, changelog, retaken screenshots, the listing sentence. After it's merged: the tag, then `scripts/release.py 1.1.0` | high (version bump, store listing) |
