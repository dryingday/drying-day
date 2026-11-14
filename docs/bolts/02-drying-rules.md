# Bolt 2: drying rules and reference set

A pure, shared engine that turns an hourly forecast into advice for today, tomorrow and the day after: a verdict, when to hang the washing out and bring it in, how dry it gets, a few hanging tips and an indoor plan. It's tested against hand-picked edge cases and against a reference set of real forecasts labelled without sight of the rules.

The engine knows nothing about Open-Meteo (bolt 3 maps its responses onto the engine's input) and nothing about text (it returns codes; bolt 5 turns them into string resources).

## In scope

### Input

- `Forecast(timeZone: TimeZone, hours: List<HourlyWeather>, days: List<DailyLight>)`. The zone is the IANA zone of the forecast's location, never the device's.
- `HourlyWeather(start: Instant, …)` for the hour from `start` to `start + 1 h`: temperature (°C), relative humidity (%), wind speed at 10 m (km/h), visibility (m, nullable), and for the same hour precipitation probability (%, nullable), precipitation (mm), maximum gust at 10 m (km/h) and mean shortwave radiation (W/m²). Dew point and cloud cover aren't inputs: humidity and temperature carry the dew point, and radiation carries the cloud.
- Open-Meteo reports these two ways (its docs, checked 2026-11-07): temperature, humidity, wind speed and visibility are instantaneous at the timestamp, while precipitation, its probability, gusts and shortwave radiation cover the hour *before* it. So the hour from `start` takes the preceding-hour values stamped `start + 1 h` and the mean of the instantaneous values at `start` and `start + 1 h`. Bolt 3's mapping and the reference-set generator both do this, and each has a test that a shower stamped 14:00 lands in the hour from 13:00. Both also clamp each relative humidity value to 100 % before averaging: models report a little over it in saturated air (102 % at Greenwich on 10 January 2025).
- `DailyLight(date: LocalDate, sunrise: Instant, sunset: Instant)`, daylight being the interval between them clipped to that local date. Open-Meteo gives polar day as midnight to midnight and polar night as sunrise equal to sunset (checked at 69.65 N, May and November 2025), and a sunset can fall after midnight on the day the midnight sun begins; clipping covers all three. Its "midnight" is at the one fixed UTC offset it uses for a whole response, though, not the zone's offset on that date, so mappings rebuild polar day and night from real local midnight, telling them apart by `daylight_duration`.
- Hours are keyed by `Instant`, not local clock time, and the engine cuts days in the forecast's zone with kotlinx-datetime. Clock times repeat or vanish at daylight-saving changes, and Open-Meteo's offset field can't be trusted for this: its historical endpoint returned `utc_offset_seconds: 0` and UTC clock times for `timezone=auto` at Greenwich in June 2025 (checked 2026-11-07).

### Loads

- `Load.LIGHT`: shirts, underwear, thin cotton, synthetics.
- `Load.HEAVY`: towels, jeans, bedding, hoodies. A mixed load is heavy: it's done when the slowest item is.

### Output

`advise(forecast, load, now: Instant): List<DayAdvice?>`, three entries: today (from `now`), tomorrow, the day after. A pure function: no clock, no default zone, no platform APIs, deterministic.

`DayAdvice`: `date`, `verdict` (`WILL_DRY`, `PARTLY_DRY`, `DONT_BOTHER`), `hangOut: Instant?`, `bringIn: Instant?`, `dryness` (0–100, how dry the load is at bring-in), `reasons` (what limited drying: `RAIN`, `DAMP_AIR`, `STILL_AIR`, `COLD`, `FOG`, `SHORT_DAYLIGHT`, `DAY_OVER`), `tips` and `indoorPlan` (codes, below).

A day gets `null`, never a guess, when it has no `DailyLight` or when any hour of its daylight from `now` onwards is missing from `hours`. Only precipitation probability and visibility are nullable in the types (rule 2 says what a null means); bolt 3's mapping drops an hour with any other value missing, so a gap in the source becomes a missing hour and a `null` day.

### The rules

Every number below is a named constant in one file, with a test on each side of each boundary. These are the proposed starting values; the reference set (below) is how they get checked, and any change to them is a high-tier PR.

**1. Usable time.** An hour counts for the part of it that lies in daylight (sunrise to sunset) and, today, after `now`. Partial hours count pro rata.

**2. Each hour is wet, damp or drying.**

- **Wet**: precipitation probability ≥ 40 %, or precipitation ≥ 0.2 mm. If probability is null, precipitation alone decides.
- **Damp**: relative humidity ≥ 95 % (air within about a degree of its dew point: dew), or visibility < 1000 m (fog or mist). A null visibility counts as clear. Earns nothing.
- Otherwise **drying**, earning points for the hour (pro rata for partial hours):

  points = humidity-and-temperature points × wind factor + sun bonus

  Humidity and temperature together, as the vapour pressure deficit, VPD = e<sub>s</sub>(T) × (1 − RH/100), with e<sub>s</sub>(T) = 0.6108 × exp(17.27 T / (T + 237.3)) kPa (Magnus):

  | VPD (kPa) | < 0.2 | 0.2–0.4 | 0.4–0.7 | 0.7–1.0 | 1.0–1.5 | 1.5–2.5 | ≥ 2.5 |
  |---|---|---|---|---|---|---|---|
  | Points | 0 | 0.5 | 1 | 1.5 | 2 | 2.5 | 3 |

  For a feel: 0.2 kPa is 6 °C at 78 % or 15 °C at 88 %; 1.0 kPa is 20 °C at 57 % or 25 °C at 68 %; 1.5 kPa is 25 °C at 52 %; 2.5 kPa is 30 °C at 41 %. The two top bands came from comparing the engine with the reference set's blind labels: with the table flat above 1.0 kPa, hot, dry days dried up to two hours later than the labels said. [docs/reference-set.md](../reference-set.md) has the comparison.

  | Wind speed (km/h) | < 5 | 5–15 | 15–30 | ≥ 30 |
  |---|---|---|---|---|
  | Factor | 0.6 | 1.0 | 1.3 | 1.5 |

  | Shortwave radiation (W/m²) | < 300 | 300–600 | ≥ 600 |
  |---|---|---|---|
  | Bonus | 0 | 0.25 | 0.5 |

  The sun bonus is only earned when the VPD points are above zero: sun doesn't dry washing in saturated air.

**3. Load needs.** `LIGHT` is dry at 6 points, `HEAVY` at 12. For a feel: a sunny 24 °C day at 40 % with a 20 km/h breeze earns 3.75 points an hour (light dry in about 1½ hours, heavy in 3¼); a grey 14 °C day at 65 % with a 20 km/h breeze earns about 1.3 (light in 4–5 hours, heavy in 9, so usually not); 6 °C at 85 % earns nothing.

**4. Windows.** A window is a run of usable time with no wet or damp hour in it: rain, dew and fog all end a window, since each would undo the work. Its hang-out is the start of the usable part of its first hour that earns points (so never before sunrise or, today, `now`). Its bring-in is the earlier of:

- the moment the load reaches its need, interpolated linearly within the hour;
- the end of the window: the start of the next wet or damp hour, or sunset.

The day's advice uses the window with the earliest bring-in among those that reach the load's need. If none does, it uses the window with the most points; a tie goes to the earlier window.

**5. Verdict.** `WILL_DRY` if the chosen window reaches 100 % of the load's need; `PARTLY_DRY` if it reaches 50 %; otherwise `DONT_BOTHER`. `dryness` is the percentage the chosen window reaches, rounded down and capped at 100, for every verdict (0 when there's no window), so 100 always means `WILL_DRY`. `DONT_BOTHER` shows no hang-out or bring-in. A day with no usable time, polar night or today after sunset, is `DONT_BOTHER`.

**6. Reasons**, for `PARTLY_DRY` and `DONT_BOTHER` only, every one that applies, in this order:

| Reason | When |
|---|---|
| `DAY_OVER` | Today had daylight and `now` is past it. When it applies, it's the only reason |
| `RAIN` | A wet hour ends the chosen window or comes before it in the day's usable time, or there's no window and the usable time has one |
| `FOG` | The same for a damp hour with low visibility |
| `COLD` | Most of the window's time is below 0.4 kPa VPD at under 10 °C |
| `DAMP_AIR` | Most of the window's time is below 0.4 kPa VPD at 10 °C or more, or the same as `RAIN` for a damp hour with high humidity (dew) |
| `STILL_AIR` | Most of the window's time has a wind factor below 1 |
| `SHORT_DAYLIGHT` | The day's usable time, from `now` today, is under 6 hours |

"Most" is more than half, by time, over the whole window: its drying time before hang-out counts too. Each hour is one kind, wet before fog before dew, so a foggy, humid hour is `FOG`. With no window at all, the "most of the window" reasons are judged over the day's usable time instead. Polar night, which has no daylight, is `SHORT_DAYLIGHT`.

**7. The weakest factor, when no reason applies.** Conditions can be mediocre on every count with none crossing its threshold: a `PARTLY_DRY` day at about 60 % dry with no reason, and a screen that shows no "Why". When the table above gives an empty list for a `PARTLY_DRY` or `DONT_BOTHER` day, the engine adds the one reason whose factor came closest to its threshold. No threshold changes. The table's own list is never altered: this applies only to an empty one.

- **When it applies.** `PARTLY_DRY` and `DONT_BOTHER` days whose list from the table is empty. Never `WILL_DRY`, which has no reasons. Never when `DAY_OVER` applies, because `DAY_OVER` is always listed alone and the list is never empty then. `DONT_BOTHER` gets it, as bolt 5's "Why" shows for both verdicts, and an empty list can happen there in two ways: a window earns under half the load's need without any reason crossing its threshold (for example a heavy load on a 12-hour day with one sunny hour, 5 cold, slow hours and 6 warm, slow ones, with wind under 5 km/h in fewer than half the hours, as `STILL_AIR` would apply otherwise), or there is no window and the day's usable time splits exactly half and half between cold, slow hours and warm, slow hours, and neither is "more than half".
- **The span.** The same one the table judges: the whole chosen window, or the day's usable time when there's no window.
- **The measure.** A closeness to each candidate's own threshold, which is 1 at the threshold. Each is at most 1 here, because the reason didn't apply.

  | Candidate | Closeness |
  |---|---|
  | `COLD` | The share of the span's time below 0.4 kPa VPD at under 10 °C, divided by 0.5 |
  | `DAMP_AIR` | The share of the span's time below 0.4 kPa VPD at 10 °C or more, divided by 0.5 |
  | `STILL_AIR` | The share of the span's time with a wind factor below 1, divided by 0.5 |
  | `SHORT_DAYLIGHT` | 6 hours divided by the day's usable time |

  Shares are by time, with partial hours pro rata, as in the table. For `DAMP_AIR` only the time share counts, as an empty list has no dew hour in or before the span.
- **Not candidates.** `RAIN` and `FOG`: an empty list means no wet or foggy hour came before or ended the window, and each is judged on a single hour rather than a share of the span. A near miss (a 35 % chance, 1 200 m of visibility) is on a different scale from the other four. Bolt 2's `SHOWERS_POSSIBLE` tip already covers 20 to 39 %. `DAY_OVER` is never added.
- **The pick.** The highest closeness wins. A tie goes to the first in the table's order (`COLD`, `DAMP_AIR`, `STILL_AIR`, `SHORT_DAYLIGHT`). Exactly one reason is added, and the list then has one entry. The comparison is exact: `Duration` values compared with integer arithmetic (cross-multiplied), with no floating point, because rounding would blur a tie.
- **No window.** The measure works as it does with a window, over the usable time. The list is empty without a window only in the half-and-half split above. Usable time is never zero when the list is empty, as no usable time means `SHORT_DAYLIGHT` or `DAY_OVER` applies. Nothing divides by zero.
- **Why `SHORT_DAYLIGHT` is a candidate.** With a window and an empty list, the window ended at sunset, because a wet, foggy or dew hour ending it would have given `RAIN`, `FOG` or `DAMP_AIR`. Daylight ran out first. Its closeness is `6 h / usable`: 0.67 on a 9-hour day, 0.5 on a 12-hour day. On a long day it wins only when every share is small.
- **The screen** shows it as any other reason: the same string, the same line. `DayAdvice` gains no field.

For a feel: a 10-hour day with 20 % of its time cold and slow has `COLD` at 0.4 and `SHORT_DAYLIGHT` at 0.6, and shows "Not enough daylight." The same day with 35 % cold, slow time has `COLD` at 0.7, and shows "Cold, damp air dries slowly."

### Tips and indoor plan

Codes only. Tips are for `WILL_DRY` and `PARTLY_DRY`, judged over the time on the line, from hang-out to bring-in; at most three per day, in this order of priority:

| Tip | When (in the time on the line) |
|---|---|
| `PEG_WELL` | Gusts ≥ 40 km/h |
| `SHOWERS_POSSIBLE` | Any hour at 20–39 % precipitation probability |
| `HEAVY_ITEMS` (pockets out, hang by hems, turn halfway) | `HEAVY` load |
| `SPACE_OUT` | Wind factor < 1 for most of the time on the line |
| `DARKS_INSIDE_OUT` | Two or more hours at ≥ 700 W/m² |

Indoor plan, for `PARTLY_DRY` (after bring-in) and `DONT_BOTHER`:

| Step | When |
|---|---|
| `AIRER_SPACED` | Always |
| `OPEN_WINDOW` | Mean outdoor relative humidity < 70 % over the indoor time |
| `DEHUMIDIFIER_OR_CLOSED_ROOM` | The same mean ≥ 70 % |
| `NOT_ON_RADIATOR` (near is fine) | Always |
| `TURN_HEAVY_ITEMS` | `HEAVY` load |

The indoor time is from bring-in to sunset for `PARTLY_DRY`, and the day's usable time for `DONT_BOTHER`. It starts no earlier than `now` and lasts at least six hours, so a bring-in at sunset or a question after dark still looks at the hours ahead. The mean is weighted by time. If any of that time is missing from the forecast, neither `OPEN_WINDOW` nor `DEHUMIDIFIER_OR_CLOSED_ROOM` is given.

### Reference set

- `scripts/fetch-reference-forecasts.py`: standard-library Python, HTTPS with a timeout. Fetches past forecasts from Open-Meteo's historical-forecast API with `timeformat=unixtime`, covering the case's day, the two days after it and a margin day either side (its hours are stamped in UTC whatever the zone), and writes:
  - the responses to `shared/src/commonTest/testdata/reference/`, with the echoed latitude and longitude rounded to two decimals, and a README giving the CC BY 4.0 attribution;
  - a generated `ReferenceForecasts.kt` in `commonTest` holding them as engine `Forecast`s, one function per case to stay under the JVM's method size limit, since common tests can't read files.
- About 36 cases at public landmarks (coordinates to two decimal places, no homes), chosen for spread rather than chance: maritime, continental, Mediterranean, Nordic and southern-hemisphere climates; clear, showery, foggy, windy, cold and humid days; both daylight-saving changes; one polar case. `now` is 08:00 local, plus a few cases at midday and late afternoon.
- **Blind labels.** A fresh subagent, given only each case's hourly data in local time, sunrise and sunset, and the verdict definitions (not the engine, the rules or the thresholds), labels each case for both loads: verdict, hang-out and bring-in to the hour, and a one-line reason. Labelling can happen before the engine exists.
- **Disagreements.** The engine runs on every case. Each disagreement goes in `docs/reference-set.md` with both answers, and the maintainer decides: the label was wrong, the rule was, or it stays open. A rule change is its own high-tier PR. An open disagreement is recorded in the document and in `decisions.json`, and left out of the test's agreement check, for evidence that's better than one labeller's judgement.
- **Reasons and the reference set.** The blind labels have a verdict and times, and a free-text reason that no test reads. Rule 7 therefore changes no labelled day. Checked on 2026-11-14 by listing the engine's days with an empty list: of 84 entries that aren't `WILL_DRY`, three are empty, all on the second or third day of a case, which have no labels (Madrid 16 February 2025 heavy, third day; Sydney 26 June 2025 heavy, second and third days). Each of the three would get `SHORT_DAYLIGHT` (about 10 hours usable), by closeness measured over the usable time. The PR that carries out rule 7 runs the same listing, records any change in `docs/reference-set.md`, and doesn't weaken a test to make one pass.
- `ReferenceSetTest` asserts every agreed case on its first day (the "today" entry): the same verdict, and hang-out and bring-in within an hour of the label, or of the maintainer's answer where the label was wrong. Every open case must still disagree, so the document stays true. The two days after only need to be non-null.
- Real coordinates are needed for real forecasts. PLAN.md allows "neutral, well-known places", and the maintainer agreed ([on PR #13](https://github.com/dryingday/drying-day/pull/13#discussion_r4503700795)) to an exception to the "no real places" rule for the reference set alone. CLAUDE.md, CONTRIBUTING.md and REVIEW.md now say so.

## Out of scope

Fetching live forecasts and mapping Open-Meteo responses (bolt 3). Text, string resources and any UI, including the load switch (bolt 5). Freezing conditions beyond what the VPD table gives, UV, pollen, air quality.

## Acceptance criteria

- `./gradlew check` passes, with the engine in `commonMain` using no Android or iOS API.
- Every threshold above is a named constant in one file, tested on both sides of every boundary.
- Tests show that a shower stamped 14:00 by Open-Meteo lands in the hour from 13:00 in the generator.
- Tests show correct hang-out and bring-in times across the spring and autumn daylight-saving changes in at least two zones in different hemispheres, a sunrise or sunset that falls mid-hour, a `now` partway through the day, and polar day and night.
- Missing hours or required fields give `null` for that day, never a verdict.
- Every reference case has a blind label for both loads; every disagreement has the maintainer's decision (label wrong, rule wrong, or open) in `docs/reference-set.md`; `ReferenceSetTest` passes on all agreed and decided cases.
- Rule 7 has tests in `ReasonsTest`, each building a `PARTLY_DRY` or `DONT_BOTHER` day and asserting the verdict as well as the list:
  - Each candidate wins alone: `COLD`, `DAMP_AIR`, `STILL_AIR` and `SHORT_DAYLIGHT`, each on a day where its closeness is the highest and below 1, with the other three given in a comment.
  - Each tie goes to the earlier reason: `COLD` over `DAMP_AIR`, `DAMP_AIR` over `STILL_AIR`, `STILL_AIR` over `SHORT_DAYLIGHT` (a 12-hour day, at 0.5, against a quarter of the time in still air).
  - An empty-list `DONT_BOTHER` day with a window, and one with no window (6 of 12 usable hours cold and slow, 6 warm and slow, with wind under 5 km/h in fewer than half the hours, as `STILL_AIR` would apply otherwise), which names `COLD` by the tie-break.
  - Exactly half the span cold and slow gets `COLD` from rule 7, and one more hour of it gets `COLD` from the table: the list is the same, the route differs.
  - A day with `now` partway through, to judge `SHORT_DAYLIGHT` on the usable time from `now`, and a sunset mid-hour, to count the shares by time.
  - Days with a non-empty list from the table are unchanged: every existing `ReasonsTest` case passes as written, and a test over the reference set checks that rule 7 changes no day whose table list is non-empty, that `WILL_DRY` stays empty and that `DAY_OVER` stays alone.
  - No `PARTLY_DRY` or `DONT_BOTHER` day in the reference set ends with an empty list.
- No real places outside the reference set, and in it public landmarks only, coordinates to two decimals; no homes or personal data.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (sets the drying thresholds) |
| 0b | The reference-set exception to "no real places" in CLAUDE.md, CONTRIBUTING.md and REVIEW.md | high (CLAUDE.md, REVIEW.md) |
| 1 | kotlinx-datetime; `Forecast`, `HourlyWeather`, `DailyLight`, `Load`, `DayAdvice` and the code enums; no logic | high (new dependency) |
| 2 | Hour classification and points: usable time, wet, damp, VPD, wind and sun tables | high (thresholds) |
| 3a | Windows, hang-out and bring-in, dryness and verdict for the three days; daylight-saving, polar and missing-data tests | high (bring-in timing) |
| 3b | Reasons | high (thresholds) |
| 4 | Tips and indoor plan | high (their cut-offs are thresholds) |
| 5 | Fetch script, fixtures and generated `ReferenceForecasts.kt`. Needs PR 1's types; can run alongside PRs 2–4. | high (`scripts/`) |
| 6a | Blind labels, the first comparison in `docs/reference-set.md`, and the two higher VPD bands it led to | high (thresholds) |
| 6 | The maintainer's decisions in `docs/reference-set.md` and `decisions.json`, and `ReferenceSetTest` | high (`scripts/`: the generator writes the test's expectations) |
| 7 | Rule 7: the weakest factor when no reason applies | medium (this spec covers it, and no threshold changes) |
