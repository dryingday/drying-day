# Bolt 4: location

The app learns where the washing is. On first run it asks for an approximate device location or a place search, and nothing is baked in. Places are kept on the device, and one is the current place, which bolt 5 fetches the forecast for.

After this bolt the placeholder screen names the current place and links to the places screen. It still shows no forecast.

## In scope

### First run

With no current place, the app opens on a screen offering two choices, and nothing else can be reached until one succeeds:

- **Use my approximate location**
- **Search for a place**

The screen says, in a few short sentences, what happens to the answer: the place stays on this device, and the app sends Open-Meteo, the weather service, only the place's rough coordinates and the user's searches, with the phone's language going only with a search. It doesn't promise more than the app controls: the phone's location service finds an approximate location, and the phone's maker or Google may run that service, which the app doesn't control. There is no default and no skip.

Success goes on to the placeholder screen.

### Device location

Used only when the user taps the button, and only once per tap. A location is **captured once and saved as a place**, not followed: a washing line doesn't move, so the app never looks again on its own, and has nothing to do in the background. Tapping the button again replaces the saved fix.

In `commonMain`, package `io.github.dryingday.location`:

```kotlin
interface DeviceLocation {
    /** Asks for permission if needed, then for one fix. */
    suspend fun locate(): DeviceLocationResult

    /** Opens this app's page in the system settings. Does nothing if the device has no such screen. */
    fun openAppSettings()
}

sealed interface DeviceLocationResult {
    data class Found(val coordinates: Coordinates) : DeviceLocationResult
    data object PermissionDenied : DeviceLocationResult
    data object LocationOff : DeviceLocationResult
    data object NoFix : DeviceLocationResult

    /** The app went to the background during the fix. */
    data object Interrupted : DeviceLocationResult
}
```

The Android implementation is in `androidApp`, package `io.github.dryingday.location`, built by `MainActivity`, which owns the permission launcher. It uses only the platform `LocationManager`. The rules that need no Android API, choosing the providers and the 24-hour limit, are pure functions in `commonMain` under `location/`, tested in `commonTest`, so `androidApp` needs no test setup.

- **Permission:** `ACCESS_COARSE_LOCATION`, and nothing else: no fine, no background. If it's already granted, no dialog. If the request is denied, the result is `PermissionDenied`, whether the dialog was answered or the system declined to show it.
- **Location services off:** `LocationManager.isLocationEnabled` from API 28, and `Settings.Secure.LOCATION_MODE` before it, gives `LocationOff`. An empty provider list isn't enough: Android 10 hides GPS from a coarse-only app (`LocationManagerService.getProviders`, AOSP `android10-release`), so a phone with location on and no network provider lists nothing. That's `NoFix`.
- **One fix:** every enabled provider but `passive` is asked at once. `getCurrentLocation` from API 30, `requestSingleUpdate` before it. The first location `Coordinates` accepts wins. A null, an invalid location, or a `SecurityException` from a provider counts as no answer from that provider, and the others carry on. All the requests are cancelled when one wins, after 30 seconds, or when the caller is cancelled. From Android 11 a coarse-only app can use GPS too, so a phone with no network provider, as many without Google Play Services are, still gets an answer. Current AOSP accepts coarse permission here (`LocationManagerService.getCurrentLocation`, checked 2026-11-09).
- **No answer**, once every provider has given none or 30 seconds have passed: the newest `getLastKnownLocation` from the same providers, skipping any that throw, if it's under 24 hours old. Otherwise `NoFix`. A line doesn't move, and the place is only as precise as a coarse fix anyway.
- **Backgrounding:** the permission dialog doesn't cancel anything. A fix runs only while the activity is started: if it stops during the fix, the requests are cancelled, the result is `Interrupted`, nothing is saved and the button is ready again with no message.
- **No reverse geocoding.** It would be another lookup of the location that the app doesn't need, so the place is shown under a fixed, translated name, "Approximate location".
- **No log calls** anywhere in the app's own Kotlin, and no coordinates in any exception message. `DeviceLocationResult` has no text field, and exceptions from providers or decoding are caught and dropped.

### What the user sees when it doesn't work

Each stays on the same screen, with the search choice still there, and each is a string resource:

| Result | Message |
|---|---|
| `PermissionDenied` | Location permission is off. Search instead, or allow it in Settings. A link opens the app's page in system Settings, and doesn't crash if nothing handles it |
| `LocationOff` | Location is off on this device. Search instead, or turn it on and try again |
| `NoFix` | Couldn't get a location. Search instead, or try again |

The button shows a waiting state during the fix and can't be tapped twice. Going back from the screen cancels the request.

### Place search

Uses the `PlaceSearchSource` from bolt 3, through a new screen.

- Sends when the user submits, with a button or the keyboard's search action. Not as they type, so unfinished text doesn't leave the device, requests stay few under the free-tier terms, and answers can't arrive out of order. A new search cancels the one running.
- `language` is the two-letter code from the Compose `Locale.current`.
- Each result shows `name`, `region` and `country`, skipping the absent ones. Tapping one saves it and makes it current.
- States, as string resources: searching, no results, and one message for `UNREACHABLE` and another for `SERVICE` and `BAD_RESPONSE`. Each failure offers another go. A query under two characters after trimming isn't sent, and the screen says why.
- After a result is chosen, search closes: to the placeholder if it was opened from first run, and back to the places screen if it was opened there.
- The `PLACES` attribution from `SourceAttributions` is shown next to results on this screen and on the places screen, as bolt 3 and Open-Meteo's terms ask. The placeholder keeps the full credit. `SourceAttributions` takes a filtered list and a text colour, so the credit can follow dark mode.

### Saved places

In `commonMain`, package `io.github.dryingday.places`.

```kotlin
sealed interface SavedPlace {
    val coordinates: Coordinates
    data class Searched(val place: Place) : SavedPlace {
        override val coordinates get() = place.coordinates
    }
    data class Device(override val coordinates: Coordinates) : SavedPlace
}
```

`toString` hides the values, as `Coordinates` and `Place` do. A `PlaceStore` holds the list and which one is current:

- **Rounded before saving** to two decimals, the same rounding the forecast request uses, so that code moves to one shared function. Nothing finer is ever written, whatever the source.
- **At most one `Device` place.** A new fix replaces it, keeps its position in the list and makes it current. The name is translated at display, so it isn't stored.
- **No duplicates among `Searched` places.** One with the same rounded coordinates as a saved `Searched` place selects that one instead of adding another. A `Device` place is never a duplicate of a `Searched` one, so the result doesn't depend on which came first.
- **New places go at the end, and adding makes one current.** Selecting changes the current place.
- **Removing the current place** makes the first remaining one current. Removing the last one returns to first run.
- No limit on the number, and no renaming.

Stored with `multiplatform-settings`, as one JSON string under one key, so a write is all or nothing. The JSON has its own private serializable types and a version, so `Coordinates` and `Place` stay free of serialization. They're plain classes, not data classes, so they have no generated `toString` to print coordinates.

- A value that can't be decoded or is a different version counts as no places, so first run shows again, and it's overwritten on the next save. It must not crash.
- An entry with invalid coordinates is dropped, and a current place that no longer exists falls back as above.
- The Android implementation is `SharedPreferencesSettings` over the app's private preferences. `allowBackup` is already off and `data_extraction_rules.xml` already excludes everything, so nothing leaves the device through backup.

### The screens

No navigation library: `App` keeps the screen in state. Three screens: first run, search and places. The places screen lists saved places with the current one marked, offers select and remove, and has the same two ways to add one, staying on the places screen after either. Removing asks for no confirmation: it's cheap to add back.

`App` takes a `PlaceStore`, a `PlaceSearchSource` and a `DeviceLocation?`. `MainActivity` builds them, with a `PlaceStore` over `SharedPreferencesSettings`. The iOS wrapper keeps compiling by passing a `PlaceStore` over `NSUserDefaultsSettings` and `null` for the location, and the location choice isn't offered then. The one `OpenMeteoSource`, which must outlive any screen, is a top-level value in `commonMain`, built with `platformHttpEngine()` on first use.

- System back goes from search to where it was opened, and from places to the placeholder.
- Rotation doesn't lose a search or a pending fix: `MainActivity` sets `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden"`, and Compose redraws.
- Built with `compose-foundation`, the only UI library in the app so far. Choosing a design system is bolt 5's decision. Buttons and rows are at least 48 dp with a button role, and text and background follow the system's light or dark setting.
- All text is in string resources.

### Dependencies

From Maven Central, versions from its metadata on 2026-11-09: `com.russhwolf:multiplatform-settings` 1.3.0, and `multiplatform-settings-test` 1.3.0 for tests. Apache-2.0. Its only dependency is the Kotlin standard library. The `-no-arg` module isn't used: the app passes in its own preferences.

### Permissions

`android.permission.ACCESS_COARSE_LOCATION`, added with the PR that first ships the Android implementation.

## Out of scope

Fetching or showing a forecast, and the offline cache (bolt 5). Forecast error text (bolt 5). Renaming places, following the device, notifications. An iOS implementation of `DeviceLocation`.

## Acceptance criteria

- `./gradlew check` passes with no network. The Kotlin compiles for iOS with `-Pdryingday.ios=true`.
- `PlaceStore` tests, using `MapSettings`, cover each rule under "Saved places": rounding, one device place replaced in position and made current, duplicates, order, current after add and remove, corrupt JSON without an exception escaping, another version, an invalid entry and a dangling current place.
- State-holder tests in `commonTest`, with a fake `DeviceLocation` and `PlaceSearchSource`, show each row of the failure table, `Interrupted` returning to ready with no message, each search state, cancellation on going back, where each success goes, and that a query is sent only on submit.
- Pure-function tests pick the providers (`passive` excluded) and apply the 24-hour limit. The merged manifest has no location permission except coarse.
- No `Log` or `println` in the app's own Kotlin, shown by a search recorded in the PR. `SavedPlace.toString()` shows no place or coordinates, with a test.
- No real places in tests or fixtures: invented names, and coordinates in open sea.
- On the emulator (API 36), coarse permission only, recorded in the PR: first run shows; denying gives the message and the Settings link; allowing with a `geo fix` saves a place; the app reopens on it; going Home during a fix cancels it; rotation keeps a search; back goes where the spec says; search finds a place and shows the credit; removing the last place returns to first run. API 26 to 35 are compile-checked only unless a device is available, and the PR says so.

## PRs

| PR | Contents | Expected tier |
|---|---|---|
| 0 | This spec | high (sets how location is handled) |
| 1 | `multiplatform-settings`, verification metadata, the shared rounding, `SavedPlace` and `PlaceStore` | high (new dependency) |
| 2 | `DeviceLocation`, its Android implementation, `ACCESS_COARSE_LOCATION` and the emulator check of a fix | high (location, `AndroidManifest.xml`) |
| 3 | The first-run screen and state, the failure messages, `configChanges` | high (location handling, `AndroidManifest.xml`) |
| 4 | The search screen, with the `PLACES` credit and back handling | high (sends search text) |
| 5 | The places screen, the placeholder link and the full emulator run | high (location handling) |
