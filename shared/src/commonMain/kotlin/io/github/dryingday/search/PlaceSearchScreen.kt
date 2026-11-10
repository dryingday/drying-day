package io.github.dryingday.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.search_back
import io.github.dryingday.shared.resources.search_button
import io.github.dryingday.shared.resources.search_label
import io.github.dryingday.shared.resources.search_none
import io.github.dryingday.shared.resources.search_searching
import io.github.dryingday.shared.resources.search_service
import io.github.dryingday.shared.resources.search_title
import io.github.dryingday.shared.resources.search_too_short
import io.github.dryingday.shared.resources.search_try_again
import io.github.dryingday.shared.resources.search_unreachable
import io.github.dryingday.weather.Attribution
import io.github.dryingday.weather.AttributionScope
import io.github.dryingday.weather.Place
import io.github.dryingday.weather.SourceAttributions
import org.jetbrains.compose.resources.stringResource

private val DARK_BACKGROUND = Color(0xFF121212)
private val DARK_BUTTON_TEXT = Color(0xFF0B1B33)

/** Searches only on the button or the keyboard's search action. [onBack] leaves without choosing. */
@Composable
fun PlaceSearchScreen(state: PlaceSearchState, attributions: List<Attribution>, onBack: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val text = if (dark) Color(0xFFE6E6E6) else Color(0xFF1B1B1B)
    val body = TextStyle(color = text, fontSize = 16.sp)

    Box(Modifier.fillMaxSize().background(if (dark) DARK_BACKGROUND else Color.White)) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row48(stringResource(Res.string.search_back), body, onBack)
            BasicText(
                stringResource(Res.string.search_title),
                style = TextStyle(color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(stringResource(Res.string.search_label), style = body)
            BasicTextField(
                value = state.query,
                onValueChange = state::updateQuery,
                singleLine = true,
                textStyle = body,
                cursorBrush = SolidColor(text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.submit() }),
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(1.dp, text, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
            SearchButton(dark) { state.submit() }
            when (val status = state.status) {
                SearchStatus.Idle -> Unit

                SearchStatus.TooShort -> BasicText(stringResource(Res.string.search_too_short), style = body)

                SearchStatus.Searching -> BasicText(stringResource(Res.string.search_searching), style = body)

                SearchStatus.NoResults -> BasicText(stringResource(Res.string.search_none), style = body)

                SearchStatus.Unreachable -> Failure(Res.string.search_unreachable, body, state)

                SearchStatus.ServiceProblem -> Failure(Res.string.search_service, body, state)

                is SearchStatus.Results ->
                    for (place in status.places) {
                        Row48(describe(place), body) { state.choose(place) }
                    }
            }
            SourceAttributions(attributions.filter { it.covers == AttributionScope.PLACES }, color = text)
        }
    }
}

@Composable
private fun Failure(message: org.jetbrains.compose.resources.StringResource, body: TextStyle, state: PlaceSearchState) {
    BasicText(stringResource(message), style = body)
    Row48(stringResource(Res.string.search_try_again), body.copy(fontWeight = FontWeight.Medium)) { state.submit() }
}

@Composable
private fun Row48(label: String, style: TextStyle, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { BasicText(label, style = style) }
}

/** Name, region and country, skipping the absent ones. */
internal fun describe(place: Place): String =
    listOfNotNull(place.name, place.region, place.country).filter { it.isNotBlank() }.joinToString(", ")

@Composable
private fun SearchButton(dark: Boolean, onClick: () -> Unit) {
    val fill = if (dark) Color(0xFF8AB4F8) else Color(0xFF1A4F9C)
    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            stringResource(Res.string.search_button),
            style = TextStyle(
                color = if (dark) DARK_BUTTON_TEXT else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
