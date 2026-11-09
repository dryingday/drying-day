# Bolt 4: location

The app learns where the washing is. On first run it asks for an approximate device location or a place search, and nothing is baked in. Places are kept on the device, and one is the current place, which bolt 5 fetches the forecast for.

After this bolt the placeholder screen names the current place and links to the places screen. It still shows no forecast.

## In scope

### First run

With no current place, the app opens on a screen offering two choices, and nothing else can be reached until one succeeds:

- **Use my approximate location**
- **Search for a place**

The screen says, in a sentence, what happens to the answer: the location stays on the device, and only its rounded coordinates go to the weather source. There is no default and no skip.

### Device location

Used only when the user taps the button, and only once per tap. A location is **captured once and saved as a place**, not followed: a washing line doesn't move, so the app never looks again on its own, and has nothing to do in the background. Tapping the button again replaces the saved fix.

In `commonMain`, package `io.github.dryingday.location`:

```kotlin
interface DeviceLocation {
    /** Asks for permission if needed, then for one fix. */
    suspend fun locate(): DeviceLocationResult
}

sealed interface DeviceLocationResult {
    data class Found(val coordinates: Coordinates) : DeviceLocationResult
    data object PermissionDenied : DeviceLocationResult
    data object LocationOff : DeviceLocationResult
    data object NoFix : DeviceLocationResult
}
```

`App` takes a `DeviceLocation?`. The iOS wrapper passes `null` until there's an iOS app, and the choice isn't offered then.

The Android implementation lives in `androidApp`, built by `MainActivity`, which owns the permission launcher. It uses only the platform `LocationManager`.

- **Permission:** `ACCESS_COARSE_LOCATION`, and nothing else: no fine, no background. If it's already granted, no dialog. If the request is denied, the result is `PermissionDenied`, whether the dialog was answered or the system declined to show it.
- **Location services off:** no enabled provider, from `getProviders(true)` minus `passive`, is `LocationOff`.
- **One fix:** every enabled provider but `passive` is asked at once. The first to answer wins, and all the requests are cancelled then, after 30 seconds, or when the caller is cancelled. `getCurrentLocation` from API 30, `requestSingleUpdate` before it. A provider that throws `SecurityException` is left out: API 26 to 30 may refuse a coarse-only app the GPS provider, and current AOSP allows it (`LocationManagerService.getCurrentLocation`, checked 2026-11-09). Asking all of them at once means a phone with no network provider, as many without Google Play Services are, still gets an answer.
- **No answer in 30 seconds:** the newest `getLastKnownLocation` from those providers, if it's under 24 hours old. Otherwise `NoFix`. A line doesn't move, and the place is only as precise as a coarse fix anyway.
- **Bad values:** a location that `Coordinates` rejects counts as no answer from that provider.
- **No reverse geocoding.** Android's `Geocoder` usually depends on Google's services and would send the location to another party, so the place is shown under a fixed, translated name, "Approximate location".
- **No log calls** anywhere under `location/` or `places/`, and no coordinates in any exception message.

### What the user sees when it doesn't work

Each stays on the same screen, with the search choice still there, and each is a string resource:

| Result | Message |
|---|---|
| `PermissionDenied` | Location permission is off. Search instead, or allow it in Settings. A link opens the app's page in system Settings, and doesn't crash if nothing handles it |
| `LocationOff` | Location is off on this device. Search instead, or turn it on and try again |
| `NoFix` | Couldn't get a location. Search instead, or try again |

The button shows a waiting state during the fix and can't be tapped twice. Leaving the screen cancels the request.

### Place search

Uses the `PlaceSearchSource` from bolt 3, through a new screen.

- Sends when the user submits, with a button or the keyboard's search action. Not as they type, so unfinished text doesn't leave the device, requests stay few under the free-tier terms, and answers can't arrive out of order. A new search cancels the one running.
- `language` is the two-letter code from the Compose `Locale.current`.
- Each result shows `name`, `region` and `country`, skipping the absent ones. Tapping one saves it and makes it current.
- States, as string resources: searching, no results, and one message for `UNREACHABLE` and another for `SERVICE` and `BAD_RESPONSE`. Each failure offers another go. A query under two characters isn't sent, and the screen says why.
- The `PLACES` attribution from `SourceAttributions` is shown on this screen whenever results are, as bolt 3 and Open-Meteo's terms ask. This needs `SourceAttributions` to take the scope to show, or a filtered list.

### Saved places

In `commonMain`, package `io.github.dryingday.places`.

```kotlin
sealed interface SavedPlace {
    val coordinates: Coordinates
    data class Searched(val place: Place) : SavedPlace
    data class Device(override val coordinates: Coordinates) : SavedPlace
}
```

`toString` hides the values, as `Coordinates` and `Place` do. A `PlaceStore` holds the list and which one is current:

- **Rounded before saving** to two decimals, the same rounding the forecast request uses, so that code moves to one shared function. Nothing finer is ever written, whatever the source.
- **At most one `Device` place.** A new fix replaces it and keeps its position in the list. The name is translated at display, so it isn't stored.
- **No duplicates.** A `Searched` place with the same rounded coordinates as a saved one selects that one instead of adding another.
- **Adding makes it current.** Selecting changes the current place.
- **Removing the current place** makes the first remaining one current. Removing the last one returns to first run.
- No limit on the number, and no renaming.

Stored with `multiplatform-settings`, as one JSON string under one key, so a write is all or nothing. The JSON has its own serializable types and a version, so `Coordinates` and `Place` stay free of serialization.

- A value that can't be decoded or is a different version counts as no places, so first run shows again, and it's overwritten on the next save. It must not crash.
- An entry with invalid coordinates is dropped, and a current place that no longer exists falls back as above.
- The Android implementation is `SharedPreferencesSettings` over the app's private preferences. `allowBackup` is already off and `data_extraction_rules.xml` already excludes everything, so nothing leaves the device through backup.

### The screens

No navigation library: `App` keeps the screen in state. Three screens: first run, search and places. The places screen lists saved places with the current one marked, offers select and remove, and has the same two ways to add one. Removing asks for no confirmation: it's cheap to add back.

- System back goes from search to where it was opened, and from places to the placeholder.
- Rotation doesn't lose a search or a pending fix: `MainActivity` handles its own configuration changes (`android:configChanges`), and Compose redraws.
- Built with `compose-foundation`, the only UI library in the app so far. Choosing a design system is bolt 5's decision. Buttons and rows are at least 48 dp with a button role, and text and background follow the system's light or dark setting.
- All text is in string resources.

### Dependencies

From Maven Central, versions from its metadata on 2026-11-09: `com.russhwolf:multiplatform-settings` 1.3.0, and `multiplatform-settings-test` 1.3.0 for tests. Apache-2.0. Its only dependency is the Kotlin standard library. The `-no-arg` module isn't used, as it needs `androidx.startup` initialisers.

### Permissions

`android.permission.ACCESS_COARSE_LOCATION`, added with the PR that first ships the Android implementation.

## Out of scope

Fetching or showing a forecast, and the offline cache (bolt 5). Forecast error text (bolt 5). Renaming places, following the device, notifications. An iOS implementation of `DeviceLocation`.

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- `PlaceStore` tests, using `MapSettings`, cover each rule under "Saved places": rounding, one device place, duplicates, current after add and remove, corrupt JSON, another version, an invalid entry and a dangling current place.
- State-holder tests in `commonTest`, with a fake `DeviceLocation` and `PlaceSearchSource`, show each row of the failure table, each search state, cancellation when leaving, and that a query is sent only on submit.
- Android: the manifest has no location permission except coarse, shown by the merged manifest. Pure functions pick the providers (`passive` excluded) and apply the 24-hour limit, with tests.
- No `Log` or `println` under `location/` or `places/`, and `SavedPlace.toString()` shows no place or coordinates, with a test.
- No real places in tests or fixtures: invented names, and coordinates that aren't anyone's home.
- On the emulator (API 36), coarse permission only, recorded in the PR: first run shows; denying gives the message and the Settings link; allowing with a `geo fix` saves a place; the app reopens on it; search finds a place and shows the credit; removing the last place returns to first run. API 26 to 35 are compile-checked only unless a device is available, and the PR says so.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (sets how location is handled) |
| 1 | `multiplatform-settings`, verification metadata, the shared rounding, `SavedPlace` and `PlaceStore` | high (new dependency) |
| 2 | `DeviceLocation`, its Android implementation, `ACCESS_COARSE_LOCATION` and the emulator check of a fix | high (location, `AndroidManifest.xml`) |
| 3 | The first-run screen and state, with the failure messages | high (location handling) |
| 4 | The search screen, with the `PLACES` credit | high (sends search text) |
| 5 | The places screen, the placeholder link, back handling and the full emulator run | medium or high, by what it touches |
