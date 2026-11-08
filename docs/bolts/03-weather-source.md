# Bolt 3: weather source and Open-Meteo

A `WeatherSource` interface and its first implementation: fetch a forecast from Open-Meteo, map it onto the engine's `Forecast`, search for places by name, and show the credit Open-Meteo's licence asks for. Tested against recorded responses, so `./gradlew check` never touches the network.

Nothing calls the source yet except a one-off check: location is bolt 4, and the main screen, caching and error messages are bolt 5.

## In scope

### The interface

In `commonMain`, package `io.github.dryingday.weather`, modelled on Breezy Weather's shape (a source plus feature interfaces, each source in its own package; design only, no code copied).

- `WeatherSource`: `id`, `attributions: List<Attribution>`.
- `ForecastSource : WeatherSource`: `suspend fun forecast(at: Coordinates): SourceResult<Forecast>`.
- `PlaceSearchSource : WeatherSource`: `suspend fun searchPlaces(query: String, language: String?): SourceResult<List<Place>>`.
- `Coordinates(latitude, longitude)`, rejecting anything out of range or not finite, with a message that doesn't include the values.
- `Place(name, region: String?, country: String?, coordinates)`.
- `Attribution(name, url, licence, licenceUrl, covers: WEATHER | PLACES)`. Names and URLs are data; the sentences around them are string resources.
- `SourceResult<T>`: `Success(value)` or `Failure(error)`, with `SourceError` one of `UNREACHABLE` (offline, DNS, TLS, timeout, a connection lost while reading the body, and any other exception), `SERVICE` (any non-2xx, including 3xx, 400 and 429) and `BAD_RESPONSE` (a complete 2xx body that can't be decoded or mapped). A `Failure` carries only that code: Ktor's exception messages contain the URL, and the URL contains the coordinates. A cancellation of the caller is rethrown, whatever the engine reports it as. No other exception leaves the source: Ktor delivers a request timeout as a cancellation of the request, and letting that out would silently end a caller that's still running, so it's `UNREACHABLE`.

Open-Meteo lives in `weather/openmeteo`.

### Forecast request

`GET https://api.open-meteo.com/v1/forecast` with:

- `latitude`, `longitude`: **rounded to two decimals in the source**, whatever the caller passes (about 1 km, no coarser than the finest model grids Open-Meteo uses), so no caller can send more.
- `hourly`: `temperature_2m,relative_humidity_2m,wind_speed_10m,visibility,precipitation_probability,precipitation,wind_gusts_10m,shortwave_radiation`.
- `daily`: `sunrise,sunset,daylight_duration`.
- `timezone=auto`, `timeformat=unixtime`.
- `past_days=1`, `forecast_days=5`. The engine needs today to the end of the day after tomorrow, plus six indoor hours. The day either side is margin: every time in a response is at one fixed UTC offset, so at a daylight-saving change the first hour of the real local day can fall outside a response that starts at "midnight"; and a forecast cached overnight (bolt 5) still covers three days. Checked 2026-11-08 at a reference-set landmark: 144 hours, 6 days, no null probabilities or visibilities.
- Units are the API's defaults, °C, km/h and mm, as the generator assumes.

### Forecast mapping

A pure function from the decoded response to `Forecast`, doing exactly what `scripts/fetch-reference-forecasts.py` does, per [bolt 2's "Input"](02-drying-rules.md):

- The hour from `start` takes `precipitation_probability`, `precipitation`, `wind_gusts_10m` and `shortwave_radiation` stamped `start + 1 h`, and the mean of `temperature_2m`, `relative_humidity_2m`, `wind_speed_10m` and `visibility` at `start` and `start + 1 h`.
- Each humidity value is clamped to 100 before averaging.
- Two stamps that aren't exactly an hour apart give no hour.
- The zone is `TimeZone.of(response.timezone)`, never the device's. Polar days and nights (`daylight_duration` 0 or ≥ 86400) are dated by `daily.time` at `utc_offset_seconds` and rebuilt from real local midnight; every other day is dated by its sunrise in the zone.

Where the generator could stop with an error, the live mapping can't, so:

| In the response | Result |
|---|---|
| An hour missing a required value, or with a value `HourlyWeather` rejects (say, a temperature of 999) | That hour is dropped, so a day that needs it is `null` |
| Visibility or precipitation probability null | Kept as null, as the engine allows. Either end of a mean being null makes the mean null |
| A day with a null `time`, `sunrise`, `sunset` or `daylight_duration`, or a sunset before its sunrise | That day is dropped |
| Two days landing on one date | Both are dropped |
| An unknown zone id (an old device's zone database), arrays of different lengths, stamps out of order, a required array absent, a body that isn't the expected JSON | `BAD_RESPONSE` |

A partial forecast is fine: the engine already says `null` for a day it can't vouch for.

### Place search

`GET https://geocoding-api.open-meteo.com/v1/search` with `name`, `count=10`, `format=json`, and `language` when given. Unknown languages are accepted and fall back (checked 2026-11-08 with `xx`).

- The query is trimmed, and the trimmed text is what's sent. Under two characters (`String.length`) returns an empty list without a request: the API returns nothing for one character.
- A response with no `results` key is an empty list, not an error (checked 2026-11-08). It's what an unmatched name returns.
- A result missing a name or coordinates, or with coordinates out of range, is skipped. `admin1` maps to `region`, and it and `country` are optional.

### HTTP

- Ktor client, with one engine per platform: `ktor-client-android` (platform `HttpURLConnection`, no OkHttp) and `ktor-client-darwin`, chosen by a committed `expect fun platformHttpEngine(): HttpClientEngine`. JSON is decoded directly with kotlinx.serialization (`ignoreUnknownKeys`), so no content-negotiation modules.
- The source takes an `HttpClientEngine` and builds its own `HttpClient`, so a caller can't leave out the settings below and tests run the production configuration over `MockEngine`.
- HTTPS only: both base URLs are constants, and `followRedirects = false`. Neither API needs redirects, so a 3xx is `SERVICE` and nothing can be sent to another host or downgraded.
- Timeouts through `HttpTimeout`, as named constants: connect 10 s, whole request 30 s. Without them the Android engine waits 100 s.
- `expectSuccess = false`, so a non-2xx reaches the source as a status, not an exception.
- No logging plugin, no cookies, no automatic retries, and the app adds no headers. The `User-Agent` is Ktor's default, `ktor-client` (Ktor 3.6.1's `mergeHeaders`, which its Android engine uses), so Android's own, which names the device model, isn't sent. Whatever else the platform's HTTP stack adds itself is outside the app's control.

### Attribution

Open-Meteo's [licence](https://open-meteo.com/en/licence) (CC BY 4.0, checked 2026-11-08) asks for credit, a link to the licence, an indication of changes, and "a link next to any location Open-Meteo data are displayed". Its geocoding docs ask for "Location data based on GeoNames" (also CC BY).

- A `SourceAttributions` composable in `commonMain` renders each source's attributions from string resources, with tappable links to the source and the licence, opened in the browser. A device with no browser must not crash when a link is tapped. It says the drying advice is worked out from the data, which covers "changes were made". Roughly: "Weather data by Open-Meteo.com (CC BY 4.0). Drying advice worked out from it by this app. Place data based on GeoNames (CC BY 4.0)."
- It goes on the placeholder screen now, so it's visible on a device. Bolt 4 shows the `PLACES` attribution next to search results, and bolt 5 keeps the full credit next to the forecast.

### Free-tier terms

The free API is for non-commercial use, under 10,000 calls a day, with limits applied "by IP addresses and application"; apps without subscriptions or advertising qualify ([terms](https://open-meteo.com/en/terms), checked 2026-11-08). Each device makes a few calls a day.

### Dependencies

Maven Central only, versions from its metadata on 2026-11-08: `io.ktor:ktor-client-core`, `-android`, `-darwin` 3.6.1; `-mock` 3.6.1 for tests; `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.12.0; `kotlinx-coroutines-test` 1.11.0 for tests (the coroutines version Ktor 3.6.1 uses); and the Kotlin serialization compiler plugin at the project's Kotlin version, whose marker is on Maven Central. All are Apache-2.0.

### Permissions

`android.permission.INTERNET` is added with the attribution PR, the first to ship the client to a device. Cleartext traffic is turned off in the manifest at the same time: Android 8, the app's minSdk, still allows it by default.

## Out of scope

Location and saved places (bolt 4). Calling the source from the app, caching, staleness, error text (bolt 5). Other weather sources and a source picker (later).

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- Every recorded reference response, run through the Kotlin mapping in a host test, gives exactly the `Forecast` the generator wrote to `ReferenceForecasts.kt`.
- Tests show that a shower stamped 14:00 lands in the hour from 13:00; humidity over 100 is clamped; each row of the table above behaves as it says; polar day and night are rebuilt from real local midnight, including across a daylight-saving change.
- Tests assert the exact request URLs, including coordinates rounded to two decimals; the 10 s and 30 s timeouts on the request; that a 3xx is `SERVICE` with no second request; and each `SourceError`, including a connection lost mid-body, with cancellation rethrown.
- Tests for place search: a query under two characters makes no request; the trimmed query is what's sent; no `results` key is an empty list; malformed or out-of-range results are skipped; `admin1` maps to `region`, and `region` and `country` may be absent.
- Fixtures add no real places. Forecast tests use the reference set's recorded responses and small synthetic JSON; place search uses invented names in the shape the live API returns.
- Coordinates don't reach a log or an error. A test shows that a `Failure` from an exception whose message holds the request URL contains none of the coordinates; there's no logging call under `weather/`; and `Coordinates`' own validation message omits the values.
- The attribution shows on a device, and every link opens.
- One real forecast request and one place search succeed through `platformHttpEngine()` on the emulator. Only the call that triggers them is a local patch, not committed. The PR records the result.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (sets the app's network calls) |
| 1 | Dependencies and verification metadata; the interface and types; no logic beyond their validation | high (new dependencies) |
| 2 | Forecast response types and mapping; the parity test against the reference set | medium (forecast handling) |
| 3 | `platformHttpEngine()`, the client configuration and the forecast request: rounding, timeouts, redirects, errors | high (new network call) |
| 4 | Place search | high (new network call) |
| 5 | Attribution, `INTERNET`, cleartext off, the emulator check | high (`AndroidManifest.xml`) |
