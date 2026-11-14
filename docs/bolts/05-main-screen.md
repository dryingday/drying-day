# Bolt 5: main screen

The app answers its question. The placeholder screen gives way to the main screen: the verdict for the current place first, then when to hang the washing out and bring it in, what held drying back, tips and the indoor plan, for today, tomorrow and the day after, with a switch between light and heavy loads. The forecast is fetched from the source bolt 3 built, kept on the device so the app still answers offline, and always shown with its age.

After this bolt the app is usable end to end. Bolt 6 makes it releasable.

## In scope

### Design system

**`compose-foundation` only, no Material.** The app keeps building its few components (buttons, the day and load switches, rows) on foundation, as bolt 4's screens do. Maven Central's metadata on 2026-11-10 has no stable `org.jetbrains.compose.material3:material3` built for Compose Multiplatform 1.12: the newest stable, 1.9.0, depends on Compose 1.9.1, and the only 1.12 build is `1.12.0-alpha03`. Material would also be the app's largest dependency for a handful of components. It can be revisited after v1.

So there's no pull-to-refresh: a Refresh button does the job.

Every component this bolt adds:

- is at least 48 dp in each direction where it can be tapped, with a button, tab or radio button role and its selected state in its semantics;
- follows the system's light or dark setting, with the text colours bolt 4 uses;
- never carries meaning by colour alone: each verdict is always written out;
- lays out without clipping at 200 % font scale, scrolling where it has to.

### The forecast

In `commonMain`, package `io.github.dryingday.forecast`.

#### When it's fetched

Only the current place's forecast is fetched, with `ForecastSource.forecast(current.coordinates)` from bolt 3. That's bolt 3's request: `GET https://api.open-meteo.com/v1/forecast` with the place's latitude and longitude rounded to two decimals (already rounded by `PlaceStore`, and rounded again by the source) and bolt 3's fixed parameters. This bolt adds no host, no parameter and no identifier. It decides only when the request is made:

"On screen" below means the app is visible (on Android, the activity is at least `STARTED`) and `App` is showing the main screen, not first run, search or places.

- **Automatically**, while on screen, when the current place has no cached forecast, or its age is `FORECAST_FRESH_FOR` = **1 hour** or more, or its age is unknown (below). This is checked when the main screen comes on screen (launch, back from the background, back from the places or search screen) and on each minute tick (below).
- **After any failed fetch**, automatic or requested, automatic attempts wait `AUTO_RETRY_AFTER` = **15 minutes**, so a phone left open offline, or a service answering 429, isn't sent a request a minute. The wait is one for the app, kept in memory only: a new current place clears it, and so does relaunching the app.
- **On request**, with the Refresh button, at any time no fetch is running. It ignores both waits.
- **Never otherwise.** No background work, no scheduled fetch, no fetch while not on screen, no retry inside a fetch (bolt 3 has none), and no fetch for places other than the current one.

One fetch at a time. Leaving the main screen, going to the background, or a change of current place cancels a running fetch: nothing is stored and no error is shown for it. A fetch that succeeds replaces the cached forecast for that place. One that fails changes nothing that's stored.

A few calls a day per device, as bolt 3's free-tier note assumes: one an hour at most while the app is open, plus the user's own refreshes.

#### What's kept

A `ForecastCache` holds, for each saved place, the last forecast fetched for it:

- **The key is the place's rounded coordinates**, the same values `PlaceStore` already keeps, so the cache holds no location the place store doesn't.
- **Each entry is** those coordinates, the fetch time (an `Instant` from the clock the app is given) and the `Forecast` the source returned: the zone's IANA id, the hours and the days, every field the engine reads and nothing else. No response body, no headers, no URL.
- **One forecast per set of coordinates a saved place has.** The API is `get(coordinates)`, `put(coordinates, fetchedAt, forecast)` and `retain(saved: Set<Coordinates>)`, which drops every entry whose coordinates no saved place has. `retain` runs with the place store's coordinates at launch (from `App`) and after every change to the store (from `PlacesState`, below): a removal, a new device fix replacing the old one, a search result added. So a place's forecast goes when no saved place has its coordinates any more, and a device place and a searched place that round to the same coordinates share one entry, which outlives either alone. With the last place removed, the cache is empty. The cache and the place store are separate writes, so a crash between them can leave an entry behind until the next `retain`, which is at the next launch at the latest.
- Stored with `multiplatform-settings`, as one JSON string under the key `forecasts`, so a write is all or nothing, with its own private serializable types and a version, like `PlaceStore`. On Android, `SharedPreferencesSettings` over a private preferences file, `forecasts`. `allowBackup` is off and `data_extraction_rules.xml` excludes every shared-preferences domain, so nothing here leaves the device through backup, and the manifest doesn't change. The iOS wrapper isn't shipped in v1; there, as with bolt 4's `places`, `NSUserDefaults` goes into device backups, and the cache adds no coordinates `places` doesn't already hold.
- A value that can't be decoded, a different version, an unknown zone id or values the engine's types reject all count as no cache, without an exception escaping. An entry that fails alone is dropped alone.
- Entry types are plain classes, so no generated `toString` prints coordinates.

#### How old it can be

A forecast's age is `now` − its fetch time. A fetch time after `now` (the clock was set back) is an **unknown age**: the forecast is shown, it's refetched as soon as the rules above allow, "Updated" gives the time and zone with no day, and it doesn't become `OutOfDate` until a fetch replaces it.

- **Shown with its age, always.** The main screen says when the forecast it's showing was fetched: "Updated today 09:40 UTC+1", "Updated yesterday 21:10 UTC+1", or "Updated Wednesday 21:10 UTC+1" for any date two or more days back, in the forecast's zone and the device's time format (below). A failed fetch leaves this time alone: it is when the forecast shown was last fetched. A failed Refresh when a forecast is cached, including one too old to show, also shows a short message at the bottom, in the same words as the error under the forecast, for a few seconds. It is a live region, built on foundation only, with no Material dependency.
- **Not used at an age of `FORECAST_TOO_OLD` = 24 hours or more.** An older forecast gives no advice at all: the screen says the forecast is out of date and offers Refresh. A day-old forecast is a poor basis for a bring-in time, and the response's `past_days=1, forecast_days=5` would otherwise let the engine answer from it for days.
- Days the cached forecast doesn't cover are `null` from the engine, and shown as such (below). The engine already refuses to guess.

#### Advice

`advise(forecast, load, now)` from bolt 2, unchanged, re-run whenever the forecast, the load or `now` changes. `now` comes from a `kotlin.time.Clock` passed in (`Clock.System` in the app, a test clock in tests) and moves on a **minute tick**: while the main screen is on screen, a coroutine wakes at each whole minute and updates `now`. So "today" moves past a bring-in time, past sunset and past local midnight while the screen is open, and coming back from the background re-runs it straight away. The tick stops when it isn't on screen.

The engine is fast enough for the main thread at three days of advice. The PR that wires it measures `advise` on the emulator and records the time; above 16 ms it moves to `Dispatchers.Default`.

#### The state holder

A `ForecastState`, held by `App` above its screens so the cache, `lastError` and the retry wait survive going to the places screen and back, and tested in `commonTest` with a fake `ForecastSource`, an in-memory `ForecastCache` over `MapSettings`, and the test scheduler's clock:

```kotlin
sealed interface ForecastStatus {
    /** Nothing usable yet, and the first fetch is running. */
    data object Loading : ForecastStatus

    /** The last automatic or requested fetch failed, and there's no usable forecast to show. */
    data class Failed(val error: SourceError) : ForecastStatus

    /**
     * A cached forecast under 24 hours old, or of unknown age. [today] is `now`'s date in [timeZone], and [days]
     * are for it and the two dates after, so a null day still has a date to label. [refreshing] while a fetch
     * runs; [lastError] if the last one failed.
     */
    data class Showing(
        val fetchedAt: Instant,
        val timeZone: TimeZone,
        val today: LocalDate,
        val days: List<DayAdvice?>,
        val refreshing: Boolean,
        val lastError: SourceError?,
    ) : ForecastStatus

    /** A cached forecast 24 hours old or more, with no fresh one yet. */
    data class OutOfDate(val fetchedAt: Instant, val refreshing: Boolean, val lastError: SourceError?) : ForecastStatus
}
```

`lastError` clears when a fetch succeeds.

### The load

A switch with two choices, **Light load** and **Heavy load**, each with a line saying what it means, from bolt 2's `Load`: "Shirts, underwear, thin cotton, synthetics" and "Towels, jeans, bedding, hoodies. Mixed loads too".

- **The default is `HEAVY`.** A mixed load is heavy, most loads are mixed, and a heavy load's verdict is the cautious one: if it says the washing will dry, a light load will too.
- **The choice is remembered** across launches, as `"LIGHT"` or `"HEAVY"` under the key `load` in a private preferences file, `settings`. Any other stored value counts as the default.
- Switching re-runs the engine and fetches nothing.

### The screen

`App` keeps the screen in state, as bolt 4 left it. The main screen replaces the placeholder; first run, search and places are unchanged, and the main screen's place name opens the places screen. System back from the main screen leaves the app, as it does from the placeholder now.

From the top:

1. **The place**: its name as bolt 4 shows it ("Approximate location" for a device fix), tappable, opening the places screen.
2. **The days**: three tabs, **Today**, **Tomorrow** and the weekday of `today` + 2 days ("Thursday"), from `Showing.today`, so a `null` day is labelled too, each with its verdict in short (Will dry, Partly, Don't bother, or a dash for a `null` day). Today is selected on launch, and again when `Showing.today` changes. The selection survives rotation (`configChanges` from bolt 4 already keeps the composition).
3. **The selected day**, in this order:
   - **The verdict**, large: **Will dry**, **Partly dry, finish indoors**, or **Don't bother, dry indoors**, as `docs/PLAN.md` words them. When today's only reason is `DAY_OVER`, the headline is **Too late to dry outside today** instead, and there's no "Why".
   - **The times**, for `WILL_DRY` and `PARTLY_DRY`: "Hang out at 10:15. Bring in by 14:30." For `PARTLY_DRY`, then: "About 70 % dry by then." The percentage is `dryness`.
   - **Why**, for `PARTLY_DRY` and `DONT_BOTHER`: the reasons, one line each, in the engine's order.
   - **Tips**, for `WILL_DRY` and `PARTLY_DRY`: the engine's tips, one line each.
   - **Indoors**, for `PARTLY_DRY` ("When you bring it in") and `DONT_BOTHER`: the indoor plan, one line each.
   - A `null` day: "There isn't enough forecast for this day yet." Nothing else.
4. **The load switch.**
5. **The forecast's status**: "Updated today 09:40 UTC+1", "Updating…" while a fetch runs, and the error below it if the last fetch failed. A Refresh button, which shows the waiting state and can't be tapped twice.
6. **The credit**: the full `SourceAttributions` from bolt 3, next to the forecast, as Open-Meteo's terms ask. It stays visible, or one scroll away at large font sizes, in every state that shows the forecast.

Without a usable forecast the screen shows the place, the load switch, the credit, and in place of the days:

| Status | Shows |
|---|---|
| `Loading` | "Getting the forecast…" |
| `Failed` | The message for the error, and Try again |
| `OutOfDate` | "The last forecast is more than a day old." The error, if a fetch failed, and Refresh |

#### Text

All of it in string resources. The copy, to start from:

| Code | Text |
|---|---|
| `RAIN` | Rain gets in the way |
| `FOG` | Fog or mist |
| `COLD` | Cold, damp air dries slowly |
| `DAMP_AIR` | The air is too damp |
| `STILL_AIR` | Not much wind |
| `SHORT_DAYLIGHT` | Not enough daylight |
| `DAY_OVER` | Today's daylight is over |
| `PEG_WELL` | Peg everything well, as it's gusty |
| `SHOWERS_POSSIBLE` | Keep an eye on the sky, as showers are possible |
| `HEAVY_ITEMS` | Pockets out, hang heavy things by the hems, and turn them halfway |
| `SPACE_OUT` | Leave space between items, as there's little wind |
| `DARKS_INSIDE_OUT` | Turn darks inside out, as strong sun fades them |
| `AIRER_SPACED` | Use an airer, with space between items |
| `OPEN_WINDOW` | Open a window, as the air outside is dry enough to help |
| `DEHUMIDIFIER_OR_CLOSED_ROOM` | Keep windows shut. Use a dehumidifier, or a small room with the door closed |
| `NOT_ON_RADIATOR` | Not on a radiator. Near one is fine |
| `TURN_HEAVY_ITEMS` | Turn heavy items now and then |

| Error | With no forecast to show | Under a forecast that's shown |
|---|---|---|
| `UNREACHABLE` | Couldn't reach the weather service. Check your connection and try again. | Couldn't reach the weather service to update the forecast. |
| `SERVICE` | The weather service had a problem. Try again in a moment. | Couldn't update, as the weather service had a problem. |
| `BAD_RESPONSE` | The weather service sent something this app can't read. Try again later. | Couldn't update, as the weather service sent something this app can't read. |

Each enum maps to its string with an exhaustive `when`, so a new code doesn't compile without text. Weekday names are string resources too, one per `DayOfWeek`.

#### Times

- **In the forecast's zone**, never the device's: the place's clock is the one the washing line keeps. The device's zone is read once a minute only to compare: when its UTC offset at `now` differs from the forecast zone's, the screen adds "Times are in the place's own time zone." under the times.
- **In the device's time format**: 12- or 24-hour as the user set it. `App` takes `formatTime: (Instant, TimeZone) -> String`. On Android, `MainActivity` passes one built on `android.text.format.DateFormat.getTimeFormat(context)` with its zone set to the forecast's. The iOS wrapper and tests pass a common 24-hour `HH:mm` formatter built with kotlinx-datetime's format builder.
- **To the minute**, as the engine gives them: the hang-out's seconds rounded up and the bring-in's down, so the times shown never fall before sunrise or `now`, or after rain or sunset. They aren't rounded to a quarter hour: rounding a `WILL_DRY` bring-in down would tell people to take in washing that isn't dry yet, rounding it up could cross the start of rain, and `DayAdvice` doesn't say which end it is. `dryness` is for the engine's bring-in, under a minute after the one shown. The two can't round into the same minute in practice: an hour earns at most 5 points (3 × 1.5 + 0.5) and a day with times needs at least 3, so its window is at least 36 minutes. If they ever did, both are shown as the hang-out's minute rounded down, with a test.
- The "Updated" time is the same format, in the forecast's zone, with that zone's UTC offset at the fetch time after it (`UTC`, `UTC+1`, `UTC-4`, `UTC+5:45`), because common code has no zone abbreviations. "Today", "yesterday" and the weekday are counted by local date in the forecast's zone.

### Wiring

`App` gains a `ForecastSource`, a `Settings` for the forecast cache, a `Settings` for the load, a `Clock`, `formatTime`, and `visible: Boolean`. `MainActivity` builds the two `SharedPreferencesSettings` (`forecasts`, `settings`), passes `sharedOpenMeteoSource` as the forecast source and `Clock.System`, and sets `visible` from its `lifecycle` being at least `STARTED`, the same lifecycle `AndroidDeviceLocation` already watches, so nothing is added. The iOS wrapper passes the standard `NSUserDefaultsSettings` for both, `Clock.System`, the common formatter, and `visible = true`. The keys (`places`, `forecasts`, `load`) differ, so one `Settings` can hold all three. iOS isn't shipped in v1, so the rules for going to the background are Android's; an iOS app would pass its own lifecycle in.

`PlacesState` takes the `ForecastCache` and calls `retain` with the remaining places' coordinates after each change it makes, so a removed place's forecast goes unless another saved place has the same coordinates. First run and search already call `PlacesState.refresh` after they save, and it calls `retain` too. PR 4 adds the parameter.

No log calls, as bolt 4 set out. `ForecastStatus` holds no coordinates.

### Dependencies

None new. `kotlin.time.Clock` is in the standard library, and everything else is already in the catalogue.

### Permissions

None new, and no change to any `AndroidManifest.xml`.

## Out of scope

Release work, the icon and store screenshots (bolt 6). Translations beyond English. Notifications, widgets, background refresh. A forecast for places other than the current one. Material 3 or any other design system. Hourly charts or the raw weather: the verdict is the product. Any change to the engine or its thresholds.

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- `ForecastCache` tests, over `MapSettings`: `retain` dropping entries no saved place has, and removing one of a device and a searched place that share coordinates keeping their entry; round trip of a `Forecast` equal to the original, including nullable fields and a zone with a daylight-saving rule; one entry per set of coordinates, replaced by a newer fetch; corrupt JSON, another version, an unknown zone and an invalid hour each without an exception escaping, and the last dropping only its own entry. A test shows the stored JSON's keys are exactly the fields listed under "What's kept".
- `ForecastState` tests, with a fake source and the test clock: each fetch trigger (no cache, 1 hour on each side, fetch time after `now`, coming on screen, back from the places screen, new current place, the minute tick); the 15-minute wait after a failed automatic attempt and after a failed Refresh, on each side, Refresh ignoring it and a new place clearing it; no fetch while not on screen; cancellation on going to the background, on leaving the main screen and on a change of place, with nothing stored; a fetch time after `now` shown as `Showing` with the plain time, and not `OutOfDate`; a failure keeping the cached forecast and setting `lastError`, and a success clearing it; 24 hours on each side of `OutOfDate`; each status; the minute tick moving today past a bring-in, past sunset and past midnight in the forecast's zone; the load default, its round trip and an unknown stored value.
- Text tests: every reason, tip, indoor step, error and weekday has a string, through the exhaustive mappings; the `DAY_OVER` headline; times to the minute with the hang-out's seconds rounded up and the bring-in's down, at a quarter-hour zone offset (UTC+05:45) and across a daylight-saving change; the weekday label of a `null` third day; a `DONT_BOTHER` day with only `DAY_OVER` showing its headline, no "Why" and its indoor steps; the "Times are in the place's own time zone" rule on each side.
- A search recorded in the PR finds no `Log` or `println` in the app's own Kotlin.
- No real places in tests or fixtures, beyond the reference set's.
- On the emulator (API 36), recorded in the PR with screenshots: the main screen for a will-dry, a partly-dry and a don't-bother day, in light and dark; switching load; switching day; rotation keeping the day; Refresh; aeroplane mode showing the cached forecast with its age and the error, and first launch offline showing the error with Try again; the clock moved forward 25 hours with the network off showing out of date with the no-connection error (with the network on, TLS may fail on the skewed clock, so that isn't the check); the time `advise` takes, against the 16 ms line; 200 % font scale with no clipping; TalkBack reading the verdict and the selected tab. The forecast for an emulator fix comes from the live API, so its verdict is whatever the day brings: the PR says which of the three it showed live, and shows the others with fixture forecasts through a fake source in a local patch that isn't committed.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (when the forecast is fetched, what's kept, the load default) |
| 1 | `ForecastCache`, its private types and tests | medium (keeps coordinates, as this spec names) |
| 2 | `ForecastState`: the fetch rules, freshness, the minute tick and the load preference, with tests | medium (makes the network call, at the times this spec names) |
| 3 | The text: string resources, the code-to-text mappings, the time formatting, with tests | medium (UI copy, bring-in time display) |
| 4 | The main screen, the wiring in `App`, `MainActivity` and the iOS wrapper, the placeholder removed, and the emulator run | medium (UI, forecast handling) |
