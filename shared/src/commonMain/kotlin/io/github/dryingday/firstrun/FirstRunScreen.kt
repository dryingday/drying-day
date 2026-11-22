package io.github.dryingday.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.dryingday.InkButton
import io.github.dryingday.Palette
import io.github.dryingday.bodyStyle
import io.github.dryingday.headingStyle
import io.github.dryingday.palette
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.first_run_locating
import io.github.dryingday.shared.resources.first_run_location_off
import io.github.dryingday.shared.resources.first_run_no_fix
import io.github.dryingday.shared.resources.first_run_open_settings
import io.github.dryingday.shared.resources.first_run_permission_denied
import io.github.dryingday.shared.resources.first_run_privacy
import io.github.dryingday.shared.resources.first_run_privacy_location
import io.github.dryingday.shared.resources.first_run_search
import io.github.dryingday.shared.resources.first_run_title
import io.github.dryingday.shared.resources.first_run_use_location
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shown while there is no current place. The location fix starts only from a tap, in the screen's own scope, so
 * leaving the screen cancels it. [onSearch] opens the place search.
 */
@Composable
fun FirstRunScreen(state: FirstRunState, onSearch: () -> Unit) {
    val colours = palette()
    val body = bodyStyle().copy(color = colours.ink)

    Box(Modifier.fillMaxSize().background(colours.page)) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                stringResource(Res.string.first_run_title),
                style = headingStyle().copy(color = colours.ink),
            )
            BasicText(stringResource(Res.string.first_run_privacy), style = body)
            LocationChoices(state, colours, body, onSearch)
        }
    }
}

/**
 * The two ways to add a place, with the location button's waiting state and failure messages. Shared by first run and
 * the places screen. The fix runs in the screen's own scope, so leaving the screen cancels it.
 */
@Composable
internal fun LocationChoices(state: FirstRunState, colours: Palette, body: TextStyle, onSearch: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.canUseLocation) {
            BasicText(stringResource(Res.string.first_run_privacy_location), style = body)
            InkButton(
                stringResource(
                    if (state.waiting) Res.string.first_run_locating else Res.string.first_run_use_location,
                ),
                colours,
                enabled = !state.waiting,
            ) { scope.launch { state.useLocation() } }
        }
        val message = state.problem?.let(::messageFor)
        if (message != null) {
            BasicText(stringResource(message), style = body)
            if (state.problem == LocationProblem.PermissionDenied) {
                Box(
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = state::openSettings),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        stringResource(Res.string.first_run_open_settings),
                        style = body.copy(textDecoration = TextDecoration.Underline),
                    )
                }
            }
        }
        InkButton(stringResource(Res.string.first_run_search), colours, onClick = onSearch)
    }
}

private fun messageFor(problem: LocationProblem): StringResource = when (problem) {
    LocationProblem.PermissionDenied -> Res.string.first_run_permission_denied
    LocationProblem.LocationOff -> Res.string.first_run_location_off
    LocationProblem.NoFix -> Res.string.first_run_no_fix
}
