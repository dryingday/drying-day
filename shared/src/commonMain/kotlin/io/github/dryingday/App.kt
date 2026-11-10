package io.github.dryingday

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryingday.firstrun.FirstRunScreen
import io.github.dryingday.firstrun.FirstRunState
import io.github.dryingday.location.DeviceLocation
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.places.PlacesScreen
import io.github.dryingday.places.PlacesState
import io.github.dryingday.places.SavedPlace
import io.github.dryingday.places.placeLabel
import io.github.dryingday.search.PlaceSearchScreen
import io.github.dryingday.search.PlaceSearchState
import io.github.dryingday.search.twoLetterLanguage
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.app_name
import io.github.dryingday.shared.resources.placeholder_current_place
import io.github.dryingday.shared.resources.placeholder_places
import io.github.dryingday.weather.PlaceSearchSource
import io.github.dryingday.weather.SourceAttributions
import io.github.dryingday.weather.openmeteo.openMeteoAttributions
import org.jetbrains.compose.resources.stringResource

private val DARK_BACKGROUND = Color(0xFF121212)

private enum class Screen { Main, Places, Search }

/**
 * First run while there is no current place, otherwise the placeholder screen until the main screen arrives in
 * bolt 5, with the places screen and the search reached from it. [deviceLocation] is null where the device can't be
 * asked. [backHandler] runs onBack for the system back gesture while enabled, and does nothing where the platform
 * has none.
 */
@Composable
fun App(
    placeStore: PlaceStore,
    searchSource: PlaceSearchSource,
    deviceLocation: DeviceLocation?,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    val places = remember(placeStore) { PlacesState(placeStore) }
    val adding = remember(placeStore, deviceLocation) { FirstRunState(deviceLocation, placeStore, places::refresh) }
    var screen by remember { mutableStateOf(Screen.Main) }
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
        Placeholder(places.current, onPlaces = { screen = Screen.Places })
    }
}

@Composable
private fun Placeholder(current: SavedPlace?, onPlaces: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val style = TextStyle(color = if (dark) Color(0xFFE6E6E6) else Color(0xFF1B1B1B), fontSize = 16.sp)
    Box(Modifier.fillMaxSize().background(if (dark) DARK_BACKGROUND else Color.White)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(stringResource(Res.string.app_name), style = style)
            if (current != null) {
                BasicText(stringResource(Res.string.placeholder_current_place, placeLabel(current)), style = style)
            }
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPlaces).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    stringResource(Res.string.placeholder_places),
                    style = style.copy(textDecoration = TextDecoration.Underline),
                )
            }
        }
        SourceAttributions(
            openMeteoAttributions,
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
            color = style.color,
        )
    }
}
