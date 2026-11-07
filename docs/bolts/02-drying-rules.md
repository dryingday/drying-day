# Bolt 2: drying rules and reference set

A pure, shared engine that turns an hourly forecast into advice for today, tomorrow and the day after: a verdict, when to hang the washing out and bring it in, how dry it gets, a few hanging tips and an indoor plan. It's tested against hand-picked edge cases and against a reference set of real forecasts labelled without sight of the rules.

The engine knows nothing about Open-Meteo (bolt 3 maps its responses onto the engine's input) and nothing about text (it returns codes; bolt 5 turns them into string resources).

## In scope

### Input

- `Forecast(timeZone: TimeZone, hours: List<HourlyWeather>, days: List<DailyLight>)`. The zone is the IANA zone of the forecast's location, never the device's.
- `HourlyWeather(start: Instant, …)` for the hour from `start`: temperature (°C), relative humidity (%), dew point (°C), precipitation probability (%, nullable), precipitation (mm), cloud cover (%), wind speed and gusts at 10 m (km/h), shortwave radiation (W/m², hour mean), visibility (m, nullable). These are the fields Open-Meteo's forecast and historical-forecast APIs return.
- `DailyLight(date: LocalDate, sunrise: Instant?, sunset: Instant?, daylightSeconds)`. Null sunrise and sunset with a full day of daylight is polar day; with none, polar night.
- Hours are keyed by `Instant`, not local clock time, and the engine cuts days in the forecast's zone with kotlinx-datetime. Clock times repeat or vanish at daylight-saving changes, and Open-Meteo's offset field can't be trusted for this: its historical endpoint returned `utc_offset_seconds: 0` and UTC clock times for `timezone=auto` at Greenwich in June 2025 (checked 2026-11-07).

### Loads

- `Load.LIGHT`: shirts, underwear, thin cotton, synthetics.
- `Load.HEAVY`: towels, jeans, bedding, hoodies. A mixed load is heavy: it's done when the slowest item is.

### Output

`advise(forecast, load, now: Instant): List<DayAdvice?>`, three entries: today (from `now`), tomorrow, the day after. A pure function: no clock, no default zone, no platform APIs, deterministic.

`DayAdvice`: `date`, `verdict` (`WILL_DRY`, `PARTLY_DRY`, `DONT_BOTHER`), `hangOut: Instant?`, `bringIn: Instant?`, `dryness` (0–100, how dry the load is at bring-in), `reasons` (what limited drying: `RAIN`, `DAMP_AIR`, `STILL_AIR`, `COLD`, `FOG`, `SHORT_DAYLIGHT`, `DAY_OVER`), `tips` and `indoorPlan` (codes, below).

A day whose daylight hours from `now` onwards aren't all in the forecast gets `null`, never a guess. So does a day with a null in a required field for one of those hours (precipitation probability and visibility may be null; see the rules).

### The rules

Every number below is a named constant in one file, with a test on each side of each boundary. These are the proposed starting values; the reference set (below) is how they get checked, and any change to them is a high-tier PR.

**1. Usable time.** An hour counts for the part of it that lies in daylight (sunrise to sunset) and, today, after `now`. Partial hours count pro rata.

**2. Each hour is wet, damp or drying.**

- **Wet**: precipitation probability ≥ 40 %, or precipitation ≥ 0.2 mm. If probability is null, precipitation alone decides.
- **Damp**: relative humidity ≥ 95 %, or visibility < 1000 m (fog or mist). Earns nothing.
- Otherwise **drying**, earning points for the hour (pro rata for partial hours):

  points = humidity-and-temperature points × wind factor + sun bonus

  Humidity and temperature together, as the vapour pressure deficit, VPD = e<sub>s</sub>(T) × (1 − RH/100), with e<sub>s</sub>(T) = 0.6108 × exp(17.27 T / (T + 237.3)) kPa (Magnus):

  | VPD (kPa) | < 0.2 | 0.2–0.4 | 0.4–0.7 | 0.7–1.0 | ≥ 1.0 |
  |---|---|---|---|---|---|
  | Points | 0 | 0.5 | 1 | 1.5 | 2 |

  For a feel: 0.2 kPa is 6 °C at 78 % or 15 °C at 88 %; 1.0 kPa is 20 °C at 57 % or 25 °C at 68 %.

  | Wind speed (km/h) | < 5 | 5–15 | 15–30 | ≥ 30 |
  |---|---|---|---|---|
  | Factor | 0.6 | 1.0 | 1.3 | 1.5 |

  | Shortwave radiation (W/m²) | < 300 | 300–600 | ≥ 600 |
  |---|---|---|---|
  | Bonus | 0 | 0.25 | 0.5 |

  The sun bonus is only earned when the VPD points are above zero: sun doesn't dry washing in saturated air.

**3. Load needs.** `LIGHT` is dry at 6 points, `HEAVY` at 12. For a feel: a sunny 24 °C day at 40 % with a 20 km/h breeze earns about 3 points an hour (light dry in 2 hours, heavy in 4); a grey 14 °C day at 65 % with a 20 km/h breeze earns about 1.3 (light in 4–5 hours, heavy in 9, so usually not); 6 °C at 85 % earns nothing.

**4. Windows.** A window is a run of usable hours with no wet hour in it. Hang-out is the start of its first drying hour that earns points. Bring-in is the earliest of:

- the moment the load reaches its need, interpolated within the hour;
- the start of the next wet hour;
- the start of the first damp hour after hang-out (dew or fog would undo the work);
- sunset.

The day's advice uses the window that gets the load dry soonest; if none does, the one that gets it driest.

**5. Verdict.** `WILL_DRY` if the window reaches 100 % of the load's need; `PARTLY_DRY` if it reaches 50 %; otherwise `DONT_BOTHER`. `dryness` is the percentage reached, capped at 100. `DONT_BOTHER` has no hang-out or bring-in.

**6. Reasons** name what held the best window back, for any verdict but a clean `WILL_DRY`: the hour types and bands that cost most points or cut the window short. `DAY_OVER` is today with no usable time left.

### Tips and indoor plan

Codes only; at most three tips per day, in this order of priority:

| Tip | When (in the chosen window) |
|---|---|
| `PEG_WELL` | Gusts ≥ 40 km/h |
| `SHOWERS_POSSIBLE` | Any hour at 20–39 % precipitation probability |
| `HEAVY_ITEMS` (pockets out, hang by hems, turn halfway) | `HEAVY` load |
| `SPACE_OUT` | Wind factor < 1 for most of the window |
| `DARKS_INSIDE_OUT` | Two or more hours at ≥ 700 W/m² |

Indoor plan, for `PARTLY_DRY` (after bring-in) and `DONT_BOTHER`:

| Step | When |
|---|---|
| `AIRER_SPACED` | Always |
| `OPEN_WINDOW` | Outdoor relative humidity < 70 % at the time |
| `DEHUMIDIFIER_OR_CLOSED_ROOM` | Outdoor relative humidity ≥ 70 % |
| `NOT_ON_RADIATOR` (near is fine) | Always |
| `TURN_HEAVY_ITEMS` | `HEAVY` load |

### Reference set

- `scripts/fetch-reference-forecasts.py`: standard-library Python, HTTPS with a timeout. Fetches past forecasts from Open-Meteo's historical-forecast API with `timeformat=unixtime`, a margin day either side of each case, and writes:
  - the raw responses to `shared/src/commonTest/testdata/reference/`, with a README giving the CC BY 4.0 attribution;
  - a generated `ReferenceForecasts.kt` in `commonTest` holding them as engine `Forecast`s, since common tests can't read files.
- About 36 cases at public landmarks (coordinates to two decimal places, no homes), chosen for spread rather than chance: maritime, continental, Mediterranean, Nordic and southern-hemisphere climates; clear, showery, foggy, windy, cold and humid days; both daylight-saving changes; one polar case. `now` is 08:00 local, plus a few cases at midday and late afternoon.
- **Blind labels.** A fresh subagent, given only each case's hourly data in local time, sunrise and sunset, and the verdict definitions (not the engine, the rules or the thresholds), labels each case for both loads: verdict, hang-out and bring-in to the hour, and a one-line reason. Labelling can happen before the engine exists.
- **Disagreements.** The engine runs on every case. Each disagreement goes in `docs/reference-set.md` with both answers, and the maintainer decides: the label was wrong, or the rule was. A rule change is its own high-tier PR.
- `ReferenceSetTest` asserts every agreed case: the same verdict, and hang-out and bring-in within an hour of the label.

## Out of scope

Fetching live forecasts and mapping Open-Meteo responses (bolt 3). Text, string resources and any UI, including the load switch (bolt 5). Freezing conditions beyond what the VPD table gives, UV, pollen, air quality.

## Acceptance criteria

- `./gradlew check` passes, with the engine in `commonMain` using no Android or iOS API.
- Every threshold above is a named constant in one file, tested on both sides of every boundary.
- Tests show correct hang-out and bring-in times across the spring and autumn daylight-saving changes in at least two zones in different hemispheres, a sunrise or sunset that falls mid-hour, a `now` partway through the day, and polar day and night.
- Missing hours or required fields give `null` for that day, never a verdict.
- Every reference case has a blind label for both loads; every disagreement has the maintainer's decision in `docs/reference-set.md`; `ReferenceSetTest` passes on all agreed cases.
- No real homes or personal data in fixtures: landmarks only, coordinates to two decimals.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (sets the drying thresholds) |
| 1 | kotlinx-datetime; `Forecast`, `HourlyWeather`, `DailyLight`, `Load`, `DayAdvice` and the code enums; no logic | high (new dependency) |
| 2 | Hour classification and points: usable time, wet, damp, VPD, wind and sun tables | high (thresholds) |
| 3 | Windows, hang-out and bring-in, dryness, verdict and reasons for the three days; daylight-saving, polar and missing-data tests | high (bring-in timing) |
| 4 | Tips and indoor plan | medium |
| 5 | Fetch script, fixtures and generated `ReferenceForecasts.kt`. Can start once this spec is merged. | high (`scripts/`) |
| 6 | Blind labels, `docs/reference-set.md` and `ReferenceSetTest` | low if no rule changes, otherwise the rule change is a separate high PR |
