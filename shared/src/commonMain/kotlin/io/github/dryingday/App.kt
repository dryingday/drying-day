package io.github.dryingday

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import com.russhwolf.settings.Settings
import io.github.dryingday.firstrun.FirstRunScreen
import io.github.dryingday.firstrun.FirstRunState
import io.github.dryingday.forecast.DayPicker
import io.github.dryingday.forecast.ForecastCache
import io.github.dryingday.forecast.ForecastState
import io.github.dryingday.forecast.ForecastStatus
import io.github.dryingday.forecast.MainScreen
import io.github.dryingday.forecast.TimeFormat
import io.github.dryingday.location.DeviceLocation
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.places.PlacesScreen
import io.github.dryingday.places.PlacesState
import io.github.dryingday.search.PlaceSearchScreen
import io.github.dryingday.search.PlaceSearchState
import io.github.dryingday.search.twoLetterLanguage
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.ForecastSource
import io.github.dryingday.weather.PlaceSearchSource
import kotlin.time.Clock

private enum class Screen { Main, Places, Search }

/**
 * First run while there is no current place, otherwise the main screen, with the places screen and the search
 * reached from it. [deviceLocation] is null where the device can't be asked. [backHandler] runs onBack for the
 * system back gesture while enabled, and does nothing where the platform has none. [forecastSettings] holds the
 * forecast cache and [settings] the load; [visible] is whether the app is on screen at all (on Android, at least
 * STARTED). [formatTime] writes a time in the device's 12- or 24-hour format. See docs/bolts/05-main-screen.md.
 */
@Composable
@Suppress("LongParameterList") // The platform wrappers pass each in by name.
fun App(
    placeStore: PlaceStore,
    searchSource: PlaceSearchSource,
    forecastSource: ForecastSource,
    deviceLocation: DeviceLocation?,
    forecastSettings: Settings,
    settings: Settings,
    clock: Clock,
    formatTime: TimeFormat,
    visible: Boolean,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    val forecastCache = remember(forecastSettings) { ForecastCache(forecastSettings) }
    val places = remember(placeStore, forecastCache) { PlacesState(placeStore, forecastCache) }
    // Forecasts of places no longer saved go at launch, in case a crash left one behind.
    LaunchedEffect(places) { places.refresh() }
    val current = places.current
    val forecast = rememberForecast(forecastSource, forecastCache, settings, clock, current?.coordinates)
    var selectedDay by remember { mutableIntStateOf(0) }
    val today = (forecast.status as? ForecastStatus.Showing)?.today
    LaunchedEffect(today) { selectedDay = 0 }
    val adding = remember(placeStore, deviceLocation) { FirstRunState(deviceLocation, placeStore, places::refresh) }
    var screen by remember { mutableStateOf(Screen.Main) }
    val onScreen = visible && screen == Screen.Main && current != null
    LaunchedEffect(onScreen) { forecast.setOnScreen(onScreen) }
    // Where search was opened from: the places screen, or first run / the placeholder.
    var searchFromPlaces by remember { mutableStateOf(false) }
    val closeSearch = {
        screen = if (searchFromPlaces && places.current != null) Screen.Places else Screen.Main
    }

    // Removing the last place leaves the places screen behind it, so adding one again lands on the placeholder.
    LaunchedEffect(places.current == null) {
        if (places.current == null &&
            screen == Screen.Places
        ) {
            screen = Screen.Main
        }
    }

    if (screen == Screen.Search) {
        backHandler(true, closeSearch)
        val scope = rememberCoroutineScope()
        val language = twoLetterLanguage(Locale.current.language)
        val state = remember(placeStore, searchSource, language) {
            PlaceSearchState(searchSource, placeStore, language, scope) {
                places.refresh()
                closeSearch()
            }
        }
        PlaceSearchScreen(state, searchSource.attributions, closeSearch)
    } else if (places.current == null) {
        FirstRunScreen(adding, onSearch = {
            searchFromPlaces = false
            screen = Screen.Search
        })
    } else if (screen == Screen.Places) {
        backHandler(true) { screen = Screen.Main }
        PlacesScreen(places, adding, searchSource.attributions, onSearch = {
            searchFromPlaces = true
            screen = Screen.Search
        }) { screen = Screen.Main }
    } else {
        MainScreen(
            place = checkNotNull(current),
            state = forecast,
            days = DayPicker(selectedDay) { selectedDay = it },
            attributions = forecastSource.attributions,
            formatTime = formatTime,
            onPlaces = { screen = Screen.Places },
        )
    }
}

/** The forecast state, held above the screens so it survives going to the places screen and back. */
@Composable
private fun rememberForecast(
    source: ForecastSource,
    cache: ForecastCache,
    settings: Settings,
    clock: Clock,
    place: Coordinates?,
): ForecastState {
    val scope = rememberCoroutineScope()
    val state = remember(source, cache, settings, clock) { ForecastState(source, cache, settings, clock, scope) }
    LaunchedEffect(place) { state.setPlace(place) }
    return state
}
