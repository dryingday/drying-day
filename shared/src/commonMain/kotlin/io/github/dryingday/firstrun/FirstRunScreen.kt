package io.github.dryingday.firstrun

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val DARK_BACKGROUND = Color(0xFF121212)
private val DARK_BUTTON_TEXT = Color(0xFF0B1B33)

/**
 * Shown while there is no current place. The location fix starts only from a tap, in the screen's own scope, so
 * leaving the screen cancels it. [onSearch] opens the place search.
 */
@Composable
fun FirstRunScreen(state: FirstRunState, onSearch: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val text = if (dark) Color(0xFFE6E6E6) else Color(0xFF1B1B1B)
    val scope = rememberCoroutineScope()
    val body = TextStyle(color = text, fontSize = 16.sp)

    Box(Modifier.fillMaxSize().background(if (dark) DARK_BACKGROUND else Color.White)) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                stringResource(Res.string.first_run_title),
                style = TextStyle(color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(stringResource(Res.string.first_run_privacy), style = body)
            if (state.canUseLocation) {
                BasicText(stringResource(Res.string.first_run_privacy_location), style = body)
                ChoiceButton(
                    stringResource(
                        if (state.waiting) Res.string.first_run_locating else Res.string.first_run_use_location,
                    ),
                    dark,
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
            ChoiceButton(stringResource(Res.string.first_run_search), dark, enabled = true, onClick = onSearch)
        }
    }
}

private fun messageFor(problem: LocationProblem): StringResource = when (problem) {
    LocationProblem.PermissionDenied -> Res.string.first_run_permission_denied
    LocationProblem.LocationOff -> Res.string.first_run_location_off
    LocationProblem.NoFix -> Res.string.first_run_no_fix
}

@Composable
private fun ChoiceButton(label: String, dark: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fill = if (dark) Color(0xFF8AB4F8) else Color(0xFF1A4F9C)
    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (dark) DARK_BUTTON_TEXT else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
