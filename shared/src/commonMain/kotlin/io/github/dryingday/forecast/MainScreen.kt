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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import io.github.dryingday.places.SavedPlace
import io.github.dryingday.places.placeLabel
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_day_missing
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
import io.github.dryingday.shared.resources.window_note
import io.github.dryingday.shared.resources.window_note_open
import io.github.dryingday.weather.Attribution
import io.github.dryingday.weather.SourceAttributions
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private val DARK_BACKGROUND = Color(0xFF121212)
private const val DAYS = 3
private const val BODY_SP = 16
private const val HEADLINE_SP = 28
private val LARGE_PICTURE = 48.dp

/** Whether each note is open. Held above the selected day, so a day without the note doesn't forget it. */
private class NoteStates {
    var load by mutableStateOf(false)
    var window by mutableStateOf(false)
}

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
    formatHour: TimeFormat,
    onPlaces: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val text = if (dark) Color(0xFFE6E6E6) else Color(0xFF1B1B1B)
    val body = TextStyle(color = text, fontSize = BODY_SP.sp)
    val notes = remember { NoteStates() }
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
            LoadToggle(state.load, state::chooseLoad, body)
            LoadNote(notes.load, { notes.load = !notes.load }, body)
            when (val status = state.status) {
                is ForecastStatus.Showing -> Showing(status, state, days, notes, formatTime, formatHour, body)

                is ForecastStatus.Loading -> Message(Res.string.forecast_loading, body)

                is ForecastStatus.Failed -> {
                    Message(status.error.textWithoutForecast(), body)
                    ActionButton(Res.string.forecast_try_again, false, body, state::refresh)
                }

                is ForecastStatus.OutOfDate -> OutOfDate(status, state, body)
            }
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
@Suppress("LongParameterList") // Each is passed in by name from MainScreen.
private fun Showing(
    status: ForecastStatus.Showing,
    state: ForecastState,
    picker: DayPicker,
    notes: NoteStates,
    formatTime: TimeFormat,
    formatHour: TimeFormat,
    body: TextStyle,
) {
    DayTabs(status, picker.selected, body, picker.onSelect)
    val day = status.days.getOrNull(picker.selected)
    if (day == null) {
        Message(Res.string.forecast_day_missing, body)
    } else {
        val date = status.today.plus(picker.selected, DateTimeUnit.DAY)
        Day(day, DayContext(status.forecast, date, state.now), notes, formatTime, formatHour, body)
    }
    val label = updatedLabel(status.fetchedAt, state.now, status.timeZone, formatTime)
    if (status.refreshing) {
        Message(Res.string.forecast_updating, body)
    } else {
        BasicText(updatedText(label), style = body)
    }
    status.lastError?.let { Message(it.textUnderForecast(), body) }
    ActionButton(Res.string.forecast_refresh, status.refreshing, body, state::refresh)
}

@Composable
private fun DayTabs(status: ForecastStatus.Showing, selected: Int, body: TextStyle, onSelect: (Int) -> Unit) {
    val stacked = LocalDensity.current.fontScale >= STACK_TABS_FROM_FONT_SCALE
    val tab = @Composable { index: Int, modifier: Modifier ->
        val isSelected = index == selected
        val text = body.color
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
            val advice = status.days.getOrNull(index)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                advice?.let { Picture(it.verdict.picture(), SMALL_PICTURE, text) }
                BasicText(
                    stringResource(advice.shortVerdict()),
                    style = body.copy(fontSize = 13.sp, fontWeight = weight),
                )
            }
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

/** What the selected day is worked out from: the forecast, the day's date and the time it is now. */
private class DayContext(val forecast: Forecast, val date: LocalDate, val now: Instant) {
    val zone: TimeZone get() = forecast.timeZone
}

@Composable
@Suppress("LongParameterList") // Each is passed in by name from the screen.
private fun Day(
    day: DayAdvice,
    context: DayContext,
    notes: NoteStates,
    formatTime: TimeFormat,
    formatHour: TimeFormat,
    body: TextStyle,
) {
    val text = body.color
    val zone = context.zone
    val now = context.now
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Picture(day.verdict.picture(), LARGE_PICTURE, text)
            BasicText(
                stringResource(day.headline()),
                Modifier.weight(1f).semantics { heading() },
                style = body.copy(fontSize = HEADLINE_SP.sp, fontWeight = FontWeight.Bold),
            )
        }
        val window = day.window()
        day.drynessLine(zone, formatTime)?.let { BasicText(it.resolve(), style = body) }
        rainLine(context.forecast, context.date, day, now, formatTime)?.let {
            BasicText(it.resolve(), style = body)
        }
        val columns = dayHours(context.forecast, context.date, day, now, formatHour)
        if (window != null) {
            val spoken = stringResource(
                Res.string.forecast_times,
                formatTime(window.hangOut, zone),
                formatTime(window.bringIn, zone),
            )
            // A day with a window always has a row; if it ever hasn't, the times are written, so they're never lost.
            if (columns.isEmpty()) {
                BasicText(spoken, style = body)
            } // Keyed on the day, so each day's row opens at its own bar rather than where the last one was left.
            else {
                key(context.date) { HoursRow(columns, window, zone, formatTime, spoken, body) }
            }
        } else if (columns.isNotEmpty()) {
            key(context.date) { HoursRow(columns, null, zone, formatTime, "", body) }
        }
        if (showsZoneNote(columns, window, zone, TimeZone.currentSystemDefault(), now)) {
            Message(Res.string.forecast_times_local, body)
        }
        if (window != null && columns.isNotEmpty()) {
            Note(Res.string.window_note, notes.window, { notes.window = !notes.window }, body) {
                BasicText(stringResource(Res.string.window_note_open), style = body)
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
