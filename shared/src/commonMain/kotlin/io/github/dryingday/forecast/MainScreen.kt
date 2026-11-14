package io.github.dryingday.forecast

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import io.github.dryingday.places.SavedPlace
import io.github.dryingday.places.placeLabel
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_day_missing
import io.github.dryingday.shared.resources.forecast_dryness
import io.github.dryingday.shared.resources.forecast_held_back
import io.github.dryingday.shared.resources.forecast_indoors
import io.github.dryingday.shared.resources.forecast_indoors_partly
import io.github.dryingday.shared.resources.forecast_loading
import io.github.dryingday.shared.resources.forecast_out_of_date
import io.github.dryingday.shared.resources.forecast_refresh
import io.github.dryingday.shared.resources.forecast_times
import io.github.dryingday.shared.resources.forecast_times_local
import io.github.dryingday.shared.resources.forecast_tips
import io.github.dryingday.shared.resources.forecast_try_again
import io.github.dryingday.shared.resources.forecast_updating
import io.github.dryingday.shared.resources.load_heavy
import io.github.dryingday.shared.resources.load_heavy_hint
import io.github.dryingday.shared.resources.load_light
import io.github.dryingday.shared.resources.load_light_hint
import io.github.dryingday.weather.Attribution
import io.github.dryingday.weather.SourceAttributions
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private val DARK_BACKGROUND = Color(0xFF121212)
private const val DAYS = 3
private const val BODY_SP = 16
private const val HEADLINE_SP = 28

/** Which of the three days is selected, and how to change it. */
class DayPicker(val selected: Int, val onSelect: (Int) -> Unit)

/**
 * The main screen: the verdict for the current place, for the selected day, with the load switch, the forecast's
 * age and the weather source's credit. Without a usable forecast it shows why instead of the days. See
 * docs/bolts/05-main-screen.md, "The screen".
 */
@Composable
@Suppress("LongParameterList") // Each is passed in by name from App.
fun MainScreen(
    place: SavedPlace,
    state: ForecastState,
    days: DayPicker,
    attributions: List<Attribution>,
    formatTime: TimeFormat,
    onPlaces: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val text = if (dark) Color(0xFFE6E6E6) else Color(0xFF1B1B1B)
    val body = TextStyle(color = text, fontSize = BODY_SP.sp)
    Box(Modifier.fillMaxSize().background(if (dark) DARK_BACKGROUND else Color.White)) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPlaces),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(placeLabel(place), style = body.copy(textDecoration = TextDecoration.Underline))
            }
            when (val status = state.status) {
                is ForecastStatus.Showing -> Showing(status, state, days, formatTime, body)

                is ForecastStatus.Loading -> Message(Res.string.forecast_loading, body)

                is ForecastStatus.Failed -> {
                    Message(status.error.textWithoutForecast(), body)
                    ActionButton(Res.string.forecast_try_again, false, body, state::refresh)
                }

                is ForecastStatus.OutOfDate -> OutOfDate(status, state, body)
            }
            if (state.status !is ForecastStatus.Showing) LoadSwitch(state, body)
            SourceAttributions(attributions, color = text)
        }
        state.refreshNotice?.let {
            RefreshNoticeBar(
                it,
                dark,
                state::dismissRefreshNotice,
                Modifier.safeDrawingPadding().align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun Message(message: StringResource, body: TextStyle) = BasicText(stringResource(message), style = body)

@Composable
private fun OutOfDate(status: ForecastStatus.OutOfDate, state: ForecastState, body: TextStyle) {
    Message(Res.string.forecast_out_of_date, body)
    status.lastError?.let { Message(it.textUnderForecast(), body) }
    ActionButton(Res.string.forecast_refresh, status.refreshing, body, state::refresh)
}

@Composable
private fun Showing(
    status: ForecastStatus.Showing,
    state: ForecastState,
    picker: DayPicker,
    formatTime: TimeFormat,
    body: TextStyle,
) {
    DayTabs(status, picker.selected, body, picker.onSelect)
    val day = status.days.getOrNull(picker.selected)
    if (day == null) {
        Message(Res.string.forecast_day_missing, body)
    } else {
        Day(day, status.timeZone, state.now, formatTime, body)
    }
    LoadSwitch(state, body)
    val label = updatedLabel(status.fetchedAt, state.now, status.timeZone, formatTime)
    if (status.refreshing) {
        Message(Res.string.forecast_updating, body)
    } else {
        BasicText(updatedText(label), style = body)
    }
    status.lastError?.let { Message(it.textUnderForecast(), body) }
    ActionButton(Res.string.forecast_refresh, status.refreshing, body, state::refresh)
}

// At large font sizes three tabs side by side break words, so they stack.
private const val STACK_TABS_FROM_FONT_SCALE = 1.5f

@Composable
private fun DayTabs(status: ForecastStatus.Showing, selected: Int, body: TextStyle, onSelect: (Int) -> Unit) {
    val stacked = LocalDensity.current.fontScale >= STACK_TABS_FROM_FONT_SCALE
    val tab = @Composable { index: Int, modifier: Modifier ->
        val isSelected = index == selected
        val weight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        Column(
            modifier.heightIn(min = 48.dp)
                .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                stringResource(dayLabel(status.today, index)),
                style = body.copy(fontWeight = weight, textDecoration = tabUnderline(isSelected)),
            )
            BasicText(
                stringResource(status.days.getOrNull(index).shortVerdict()),
                style = body.copy(fontSize = 13.sp, fontWeight = weight),
            )
        }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth()) { for (index in 0 until DAYS) tab(index, Modifier.fillMaxWidth()) }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in 0 until DAYS) tab(index, Modifier.weight(1f))
        }
    }
}

// The selected tab is bold and underlined: not colour alone.
private fun tabUnderline(selected: Boolean) = if (selected) TextDecoration.Underline else TextDecoration.None

@Composable
private fun Day(day: DayAdvice, zone: TimeZone, now: Instant, formatTime: TimeFormat, body: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(
            stringResource(day.headline()),
            Modifier.semantics { heading() },
            style = body.copy(fontSize = HEADLINE_SP.sp, fontWeight = FontWeight.Bold),
        )
        day.window()?.let { window ->
            BasicText(
                stringResource(
                    Res.string.forecast_times,
                    formatTime(window.hangOut, zone),
                    formatTime(window.bringIn, zone),
                ),
                style = body,
            )
            if (timesAreLocalToPlace(zone, TimeZone.currentSystemDefault(), now)) {
                Message(Res.string.forecast_times_local, body)
            }
            if (day.verdict == Verdict.PARTLY_DRY) {
                BasicText(stringResource(Res.string.forecast_dryness, day.drynessText()), style = body)
            }
        }
        val dayOverOnly = day.reasons == listOf(Reason.DAY_OVER)
        if (day.verdict != Verdict.WILL_DRY && !dayOverOnly) {
            Section(Res.string.forecast_held_back, day.reasons.map { it.text() }, body)
        }
        if (day.verdict != Verdict.DONT_BOTHER) Section(Res.string.forecast_tips, day.tips.map { it.text() }, body)
        if (day.verdict != Verdict.WILL_DRY) {
            val title = if (day.verdict ==
                Verdict.PARTLY_DRY
            ) {
                Res.string.forecast_indoors_partly
            } else {
                Res.string.forecast_indoors
            }
            Section(title, day.indoorPlan.map { it.text() }, body)
        }
    }
}

@Composable
private fun Section(title: StringResource, lines: List<StringResource>, body: TextStyle) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(stringResource(title), style = body.copy(fontWeight = FontWeight.Bold))
        for (line in lines) BasicText(stringResource(line), style = body)
    }
}

@Composable
private fun LoadSwitch(state: ForecastState, body: TextStyle) {
    Column(Modifier.fillMaxWidth()) {
        for ((load, label, hint) in listOf(
            Triple(Load.LIGHT, Res.string.load_light, Res.string.load_light_hint),
            Triple(Load.HEAVY, Res.string.load_heavy, Res.string.load_heavy_hint),
        )) {
            val selected = state.load == load
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { state.chooseLoad(load) })
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The mark is for the eye; the selected state is in the row's semantics.
                BasicText(if (selected) "●" else "○", Modifier.clearAndSetSemantics {}, style = body)
                Column {
                    BasicText(
                        stringResource(label),
                        style = body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                    )
                    BasicText(stringResource(hint), style = body.copy(fontSize = 13.sp))
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: StringResource, waiting: Boolean, body: TextStyle, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp)
            .clickable(enabled = !waiting, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            stringResource(if (waiting) Res.string.forecast_updating else label),
            style = body.copy(textDecoration = TextDecoration.Underline),
        )
    }
}
