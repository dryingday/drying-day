package io.github.dryingday.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryingday.firstrun.FirstRunState
import io.github.dryingday.firstrun.LocationChoices
import io.github.dryingday.search.describe
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.approximate_location
import io.github.dryingday.shared.resources.places_back
import io.github.dryingday.shared.resources.places_current
import io.github.dryingday.shared.resources.places_open_advice
import io.github.dryingday.shared.resources.places_remove
import io.github.dryingday.shared.resources.places_select
import io.github.dryingday.shared.resources.places_title
import io.github.dryingday.weather.Attribution
import io.github.dryingday.weather.AttributionScope
import io.github.dryingday.weather.SourceAttributions
import org.jetbrains.compose.resources.stringResource
private val DARK_BACKGROUND = Color(0xFF121212)

/** The name to show for a place. A device place has none stored, so it gets a fixed translated one. */
@Composable
fun placeLabel(place: SavedPlace): String = when (place) {
    is SavedPlace.Searched -> describe(place.place)
    is SavedPlace.Device -> stringResource(Res.string.approximate_location)
}

/**
 * The saved places, with the current one marked. Tap a row to make it current, or the current one to see its
 * drying advice. [adding] is the same two ways to add a place as first run, and the screen stays put after
 * either unless [onSearch] opens the search.
 */
@Composable
fun PlacesScreen(
    state: PlacesState,
    adding: FirstRunState,
    attributions: List<Attribution>,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val text = if (dark) Color(0xFFE6E6E6) else Color(0xFF1B1B1B)
    val body = TextStyle(color = text, fontSize = 16.sp)

    Box(Modifier.fillMaxSize().background(if (dark) DARK_BACKGROUND else Color.White)) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { BasicText(stringResource(Res.string.places_back), style = body) }
            BasicText(
                stringResource(Res.string.places_title),
                style = TextStyle(color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            for (place in state.places) {
                PlaceRow(place, place == state.current, body, onSelect = { state.tap(place, onBack) }) {
                    state.remove(place)
                }
            }
            LocationChoices(adding, dark, body, onSearch)
            SourceAttributions(attributions.filter { it.covers == AttributionScope.PLACES }, color = text)
        }
    }
}

@Composable
private fun PlaceRow(
    place: SavedPlace,
    isCurrent: Boolean,
    body: TextStyle,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(
                onClickLabel = stringResource(
                    if (isCurrent) Res.string.places_open_advice else Res.string.places_select,
                ),
                role = Role.Button,
                onClick = onSelect,
            ),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(placeLabel(place), style = if (isCurrent) body.copy(fontWeight = FontWeight.Bold) else body)
            if (isCurrent) BasicText(stringResource(Res.string.places_current), style = body.copy(fontSize = 13.sp))
        }
        Box(
            Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                stringResource(Res.string.places_remove),
                style = body.copy(textDecoration = TextDecoration.Underline),
            )
        }
    }
}
