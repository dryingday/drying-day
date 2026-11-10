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

Only the current place's forecast is fetched, with `ForecastSource.forecast(current.coordinates)` from bolt 3. That's the request bolt 3 names, to the host it names, with the coordinates already rounded to two decimals by `PlaceStore`. This bolt adds no host and sends nothing else. It decides only when the request is made:

- **Automatically**, when the main screen is visible and the current place has no cached forecast, or one fetched `FORECAST_FRESH_FOR` = **1 hour** or more ago, or one whose fetch time is after `now` (the clock was changed). This is checked when the screen becomes visible (launch, coming back to the foreground, a new current place) and on each minute tick (below). After an automatic attempt fails, the next automatic attempt waits `AUTO_RETRY_AFTER` = **15 minutes**, so a phone left open offline, or a service answering 429, isn't sent a request a minute.
- **On request**, with the Refresh button, at any time no fetch is running. It ignores both waits.
- **Never otherwise.** No background work, no scheduled fetch, no fetch while the app isn't visible, no retry inside a fetch (bolt 3 has none), and no fetch for places other than the current one.

One fetch at a time. Going to the background, or changing the current place, cancels a running fetch: nothing is stored and no error is shown for it. A fetch that succeeds replaces the cached forecast for that place. One that fails changes nothing that's stored.

A few calls a day per device, as bolt 3's free-tier note assumes: one an hour at most while the app is open, plus the user's own refreshes.

#### What's kept

A `ForecastCache` holds, for each saved place, the last forecast fetched for it:

- **The key is the place's rounded coordinates**, the same values `PlaceStore` already keeps, so the cache holds no location the place store doesn't.
- **Each entry is** those coordinates, the fetch time (an `Instant` from the clock the app is given) and the `Forecast` the source returned: the zone's IANA id, the hours and the days, every field the engine reads and nothing else. No response body, no headers, no URL.
- **One forecast per saved place.** Removing a place removes its forecast at once. Entries for coordinates no saved place has are dropped on each write. With the last place removed, the cache is empty.
- Stored with `multiplatform-settings`, as one JSON string under the key `forecasts`, so a write is all or nothing, with its own private serializable types and a version, like `PlaceStore`. On Android, `SharedPreferencesSettings` over a private preferences file, `forecasts`. `allowBackup` is off and `data_extraction_rules.xml` excludes every shared-preferences domain, so nothing here leaves the device through backup, and the manifest doesn't change.
- A value that can't be decoded, a different version, an unknown zone id or values the engine's types reject all count as no cache, without an exception escaping. An entry that fails alone is dropped alone.
- Entry types are plain classes, so no generated `toString` prints coordinates.

#### How old it can be

- **Shown with its age, always.** The main screen says when the forecast it's showing was fetched: "Updated 09:40", or "Updated yesterday, 21:10" when that was an earlier local date, in the device's time format (below).
- **Not used past `FORECAST_TOO_OLD` = 24 hours.** An older forecast gives no advice at all: the screen says the forecast is out of date and offers Refresh. A day-old forecast is a poor basis for a bring-in time, and the response's `past_days=1, forecast_days=5` would otherwise let the engine answer from it for days.
- Days the cached forecast doesn't cover are `null` from the engine, and shown as such (below). The engine already refuses to guess.

#### Advice

`advise(forecast, load, now)` from bolt 2, unchanged, re-run whenever the forecast, the load or `now` changes. `now` comes from a `kotlin.time.Clock` passed in (`Clock.System` in the app, a test clock in tests) and moves on a **minute tick**: while the main screen is visible, a coroutine wakes at each whole minute and updates `now`. So "today" moves past a bring-in time, past sunset and past local midnight while the screen is open, and coming back from the background re-runs it straight away. The tick stops when the screen isn't visible.

The engine is fast enough for the main thread at three days of advice. The PR that wires it measures `advise` on the emulator and records the time; above 16 ms it moves to `Dispatchers.Default`.

#### The state holder

A `ForecastState`, tested in `commonTest` with a fake `ForecastSource`, an in-memory `ForecastCache` over `MapSettings`, and the test scheduler's clock:

```kotlin
sealed interface ForecastStatus {
    /** Nothing usable yet, and the first fetch is running. */
    data object Loading : ForecastStatus

    /** The last automatic or requested fetch failed, and there's no usable forecast to show. */
    data class Failed(val error: SourceError) : ForecastStatus

    /** A cached forecast under 24 hours old. [refreshing] while a fetch runs; [lastError] if the last one failed. */
    data class Showing(
        val fetchedAt: Instant,
        val timeZone: TimeZone,
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

A switch with two choices, **Light load** and **Heavy load**, each with a line saying what it means, from bolt 2's `Load`: "Shirts, underwear, thin cotton, synthetics" and "Towels, jeans, bedding. Mixed loads too".

- **The default is `HEAVY`.** A mixed load is heavy, most loads are mixed, and a heavy load's verdict is the cautious one: if it says the washing will dry, a light load will too.
- **The choice is remembered** across launches, as `"LIGHT"` or `"HEAVY"` under the key `load` in a private preferences file, `settings`. Any other stored value counts as the default.
- Switching re-runs the engine and fetches nothing.

### The screen

`App` keeps the screen in state, as bolt 4 left it. The main screen replaces the placeholder; first run, search and places are unchanged, and the main screen's place name opens the places screen. System back from the main screen leaves the app, as it does from the placeholder now.

From the top:

1. **The place**: its name as bolt 4 shows it ("Approximate location" for a device fix), tappable, opening the places screen.
2. **The days**: three tabs, **Today**, **Tomorrow** and the third day's weekday ("Thursday"), each with its verdict in short (Will dry, Partly, Don't bother, or a dash for a `null` day). Today is selected on launch, and again when the date in the forecast's zone changes. The selection survives rotation (`configChanges` from bolt 4 already keeps the composition).
3. **The selected day**, in this order:
   - **The verdict**, large: **Will dry**, **Partly dry, finish indoors**, or **Don't bother, dry indoors**, as `docs/PLAN.md` words them. When today's only reason is `DAY_OVER`, the headline is **Too late to dry outside today** instead, and there's no "Held back by".
   - **The times**, for `WILL_DRY` and `PARTLY_DRY`: "Hang out at 10:15. Bring in by 14:30." For `PARTLY_DRY`, then: "About 70 % dry by then." The percentage is `dryness`.
   - **Held back by**, for `PARTLY_DRY` and `DONT_BOTHER`: the reasons, one line each, in the engine's order.
   - **Tips**, for `WILL_DRY` and `PARTLY_DRY`: the engine's tips, one line each.
   - **Indoors**, for `PARTLY_DRY` ("When you bring it in") and `DONT_BOTHER`: the indoor plan, one line each.
   - A `null` day: "There isn't enough forecast for this day yet." Nothing else.
4. **The load switch.**
5. **The forecast's status**: "Updated 09:40", "Updating…" while a fetch runs, and the error below it if the last fetch failed. A Refresh button, which shows the waiting state and can't be tapped twice.
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
| `PEG_WELL` | Gusty: peg everything well |
| `SHOWERS_POSSIBLE` | Showers are possible: keep an eye on the sky |
| `HEAVY_ITEMS` | Pockets out, hang heavy things by the hems, and turn them halfway |
| `SPACE_OUT` | Little wind: leave space between items |
| `DARKS_INSIDE_OUT` | Strong sun: turn darks inside out so they don't fade |
| `AIRER_SPACED` | Use an airer, with space between items |
| `OPEN_WINDOW` | Open a window: the air outside is dry enough to help |
| `DEHUMIDIFIER_OR_CLOSED_ROOM` | Keep windows shut. Use a dehumidifier, or a small room with the door closed |
| `NOT_ON_RADIATOR` | Not on a radiator. Near one is fine |
| `TURN_HEAVY_ITEMS` | Turn heavy items now and then |

| Error | With no forecast to show | Under a forecast that's shown |
|---|---|---|
| `UNREACHABLE` | Couldn't reach the weather service. Check your connection and try again. | Couldn't update: no connection. |
| `SERVICE` | The weather service had a problem. Try again in a moment. | Couldn't update: the weather service had a problem. |
| `BAD_RESPONSE` | The forecast couldn't be read. Try again later. | Couldn't update: the forecast couldn't be read. |

Each enum maps to its string with an exhaustive `when`, so a new code doesn't compile without text. Weekday names are string resources too, one per `DayOfWeek`.

#### Times

- **In the forecast's zone**, never the device's: the place's clock is the one the washing line keeps. The device's zone is read once a minute only to compare: when its UTC offset at `now` differs from the forecast zone's, the screen adds "Times are local to the place." under the times.
- **In the device's time format**: 12- or 24-hour as the user set it. `App` takes `formatTime: (Instant, TimeZone) -> String`. On Android, `MainActivity` passes one built on `android.text.format.DateFormat.getTimeFormat(context)` with its zone set to the forecast's. The iOS wrapper and tests pass a common 24-hour `HH:mm` formatter built with kotlinx-datetime's format builder.
- **Rounded to the quarter hour** on the forecast zone's clock: hang-out **up** and bring-in **down**, so the times shown sit inside the window the engine chose and never before sunrise, `now`, or after the rain. If that would put the hang-out at or after the bring-in, both are shown to the minute instead, hang-out rounded up and bring-in down. `dryness` is unchanged by the rounding. The engine's times are within an hour of the reference labels, so a quarter hour adds nothing they can be trusted to. `TIME_STEP` = 15 minutes is a named constant with tests on each side.
- The "Updated" time is the same format, in the forecast's zone.

### Wiring

`App` gains a `ForecastSource`, a `Settings` for the forecast cache, a `Settings` for the load, a `Clock`, `formatTime`, and `visible: Boolean`. `MainActivity` builds the two `SharedPreferencesSettings` (`forecasts`, `settings`), passes `sharedOpenMeteoSource` as the forecast source and `Clock.System`, and sets `visible` from its `lifecycle` being at least `STARTED`, the same lifecycle `AndroidDeviceLocation` already watches, so nothing is added. The iOS wrapper passes the standard `NSUserDefaultsSettings` for both, `Clock.System`, the common formatter, and `visible = true`. The keys (`places`, `forecasts`, `load`) differ, so one `Settings` can hold all three.

`PlacesState.remove` removes the place's forecast from the cache in the same call, so it can't be left behind.

No log calls, as bolt 4 set out. `ForecastStatus` holds no coordinates.

### Dependencies

None new. `kotlin.time.Clock` is in the standard library, and everything else is already in the catalogue.

### Permissions

None new, and no change to any `AndroidManifest.xml`.

## Out of scope

Release work, the icon and store screenshots (bolt 6). Translations beyond English. Notifications, widgets, background refresh. A forecast for places other than the current one. Material 3 or any other design system. Hourly charts or the raw weather: the verdict is the product. Any change to the engine or its thresholds.

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- `ForecastCache` tests, over `MapSettings`: round trip of a `Forecast` equal to the original, including nullable fields and a zone with a daylight-saving rule; one entry per place, replaced by a newer fetch; removal of a place's entry; pruning to saved places; corrupt JSON, another version, an unknown zone and an invalid hour each without an exception escaping, and the last dropping only its own entry. A test shows the stored JSON's keys are exactly the fields listed under "What's kept".
- `ForecastState` tests, with a fake source and the test clock: each fetch trigger (no cache, 1 hour on each side, fetch time after `now`, becoming visible, new current place, the minute tick); the 15-minute wait after a failed automatic attempt on each side, and Refresh ignoring it; no fetch while not visible; cancellation on going to the background and on a change of place, with nothing stored; a failure keeping the cached forecast and setting `lastError`, and a success clearing it; 24 hours on each side of `OutOfDate`; each status; the minute tick moving today past a bring-in, past sunset and past midnight in the forecast's zone; the load default, its round trip and an unknown stored value.
- Text tests: every reason, tip, indoor step, error and weekday has a string, through the exhaustive mappings; the `DAY_OVER` headline; the quarter-hour rounding on each side, at a quarter-hour zone offset (UTC+05:45), across a daylight-saving change, and the fallback to minutes; the "Times are local" rule on each side.
- A search recorded in the PR finds no `Log` or `println` in the app's own Kotlin.
- No real places in tests or fixtures, beyond the reference set's.
- On the emulator (API 36), recorded in the PR with screenshots: the main screen for a will-dry, a partly-dry and a don't-bother day, in light and dark; switching load; switching day; rotation keeping the day; Refresh; aeroplane mode showing the cached forecast with its age and the error, and first launch offline showing the error with Try again; the clock moved forward 25 hours showing out of date; 200 % font scale with no clipping; TalkBack reading the verdict and the selected tab. The forecast for an emulator fix comes from the live API, so its verdict is whatever the day brings: the PR says which of the three it showed live, and shows the others with fixture forecasts through a fake source in a local patch that isn't committed.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (when the forecast is fetched, what's kept, the load default) |
| 1 | `ForecastCache`, its private types and tests | medium (keeps coordinates, as this spec names) |
| 2 | `ForecastState`: the fetch rules, freshness, the minute tick and the load preference, with tests | medium (makes the network call, at the times this spec names) |
| 3 | The text: string resources, the code-to-text mappings, the time formatting and rounding, with tests | medium (UI copy, bring-in time display) |
| 4 | The main screen, the wiring in `App`, `MainActivity` and the iOS wrapper, the placeholder removed, and the emulator run | medium (UI, forecast handling) |
