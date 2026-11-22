package io.github.dryingday.forecast

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.dryingday.FocusRing
import io.github.dryingday.InkButton
import io.github.dryingday.Palette
import io.github.dryingday.StatusBarIcons
import io.github.dryingday.bodyStyle
import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import io.github.dryingday.headingStyle
import io.github.dryingday.palette
import io.github.dryingday.places.SavedPlace
import io.github.dryingday.places.placeLabel
import io.github.dryingday.shared.resources.Res
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

private val SIDE = 16.dp
private val PEG_BULLET_WIDTH = 6.dp
private val PEG_BULLET_HEIGHT = 14.dp
private val PEG_BULLET_CORNER = 2.dp
private val PEG_BULLET_TOP = 3.dp
private val STITCH_WIDTH = 2.dp
private val STITCH_DASH = 6.dp
private val STITCH_GAP = 4.dp

/** Whether each note is open. Held above the selected day, so a day without the note doesn't forget it. */
private class NoteStates {
    var load by mutableStateOf(false)
    var window by mutableStateOf(false)
}

/** Which of the three days is selected, and how to change it. */
class DayPicker(val selected: Int, val onSelect: (Int) -> Unit)

/** The screen's colours and body text, worked out once. */
private class ScreenLook(val palette: Palette, val body: TextStyle, val heading: TextStyle)

/**
 * The main screen: the sky with the place and the washing line, the load toggle under it, then the selected day's
 * washing, hours and sections, the forecast's age and the weather source's credit. Without a usable forecast the
 * line is empty and the screen says why. See docs/bolts/09-washing-line.md, "The main screen".
 */
@Composable
@Suppress("LongParameterList", "LongMethod") // Each is passed in by name from App; the screen, top to bottom.
fun MainScreen(
    place: SavedPlace,
    state: ForecastState,
    days: DayPicker,
    attributions: List<Attribution>,
    formatTime: TimeFormat,
    formatHour: TimeFormat,
    onPlaces: () -> Unit,
    statusBarIcons: (darkIcons: Boolean) -> Unit = {},
) {
    val colours = palette()
    val animations = animationsEnabled()
    val look = ScreenLook(colours, bodyStyle().copy(color = colours.ink), headingStyle().copy(color = colours.ink))
    val notes = remember { NoteStates() }
    val status = state.status
    val showing = status as? ForecastStatus.Showing
    val cloths = showing?.let { clothDays(it, state.now, animations, formatTime) }.orEmpty()
    val sky = showing?.let {
        val selected = days.selected
        skyChoice(it.forecast, it.today.plus(selected, DateTimeUnit.DAY), it.days.getOrNull(selected), state.now)
    } ?: NO_FORECAST_SKY
    StatusBarIcons(colours.sky(sky.kind).ink, statusBarIcons)
    // Only while something on the line or in the sky moves.
    val clock = rememberFrameClock(animations && (sky.rain || cloths.any { it.motion.dripsSmall > 0 }))
    val clothLook = clothLook(colours, animations, clock)
    val indication = remember(colours) { FocusRing(colours.laundryBlue, colours.ink) }
    CompositionLocalProvider(LocalIndication provides indication) {
        Box(Modifier.fillMaxSize().background(colours.page)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SkyScene(sky, colours, animations, clock, placeLabel(place), onPlaces) { ink ->
                    ClothLine(cloths, showing?.today, days.selected, days.onSelect, ink, clothLook)
                }
                Column(
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ).padding(SIDE),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LoadToggle(state.load, state::chooseLoad, look.body, colours)
                    LoadNote(notes.load, { notes.load = !notes.load }, look.body)
                    when (status) {
                        is ForecastStatus.Showing -> Showing(status, state, days, notes, formatTime, formatHour, look)

                        is ForecastStatus.Loading -> Message(Res.string.forecast_loading, look.body)

                        is ForecastStatus.Failed -> {
                            Message(status.error.textWithoutForecast(), look.body)
                            InkButton(stringResource(Res.string.forecast_try_again), colours, onClick = state::refresh)
                        }

                        is ForecastStatus.OutOfDate -> OutOfDate(status, state, look)
                    }
                    SourceAttributions(attributions, color = colours.muted)
                }
            }
            state.refreshNotice?.let {
                RefreshNoticeBar(
                    it,
                    colours,
                    state::dismissRefreshNotice,
                    Modifier.safeDrawingPadding().align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun Message(message: StringResource, body: TextStyle) = BasicText(stringResource(message), style = body)

@Composable
private fun OutOfDate(status: ForecastStatus.OutOfDate, state: ForecastState, look: ScreenLook) {
    Message(Res.string.forecast_out_of_date, look.body)
    status.lastError?.let { Message(it.textUnderForecast(), look.body) }
    RefreshButton(status.refreshing, look.palette, state::refresh)
}

@Composable
private fun RefreshButton(refreshing: Boolean, palette: Palette, onClick: () -> Unit) = InkButton(
    stringResource(if (refreshing) Res.string.forecast_updating else Res.string.forecast_refresh),
    palette,
    enabled = !refreshing,
    onClick = onClick,
)

@Composable
@Suppress("LongParameterList") // Each is passed in by name from MainScreen.
private fun Showing(
    status: ForecastStatus.Showing,
    state: ForecastState,
    picker: DayPicker,
    notes: NoteStates,
    formatTime: TimeFormat,
    formatHour: TimeFormat,
    look: ScreenLook,
) {
    val day = status.days.getOrNull(picker.selected)
    // A null day says so on its cloth; there's nothing more for it here.
    if (day != null) {
        val date = status.today.plus(picker.selected, DateTimeUnit.DAY)
        Day(day, DayContext(status.forecast, date, state.now), notes, formatTime, formatHour, look)
    }
    val label = updatedLabel(status.fetchedAt, state.now, status.timeZone, formatTime)
    val muted = look.body.copy(color = look.palette.muted)
    if (status.refreshing) {
        Message(Res.string.forecast_updating, muted)
    } else {
        BasicText(updatedText(label), style = muted)
    }
    status.lastError?.let { Message(it.textUnderForecast(), look.body) }
    RefreshButton(status.refreshing, look.palette, state::refresh)
}

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
    look: ScreenLook,
) {
    val zone = context.zone
    val now = context.now
    val body = look.body
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val window = day.window()
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
            } else {
                // Keyed on the day, so each day's row opens at its own washing rather than where the last one was left.
                key(context.date) { HoursRow(columns, window, zone, formatTime, spoken, context.date, look.palette) }
            }
        } else if (columns.isNotEmpty()) {
            key(context.date) { HoursRow(columns, null, zone, formatTime, "", context.date, look.palette) }
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
            Section(Res.string.forecast_held_back, day.reasons.map { it.text() }, look)
        }
        if (day.verdict != Verdict.DONT_BOTHER) Section(Res.string.forecast_tips, day.tips.map { it.text() }, look)
        if (day.verdict != Verdict.WILL_DRY) {
            val title = if (day.verdict == Verdict.PARTLY_DRY) {
                Res.string.forecast_indoors_partly
            } else {
                Res.string.forecast_indoors
            }
            Section(title, day.indoorPlan.map { it.text() }, look)
        }
    }
}

/** A heading in Big Shoulders, then each line led by a small peg, the lines divided by a dashed rule like stitching. */
@Composable
private fun Section(title: StringResource, lines: List<StringResource>, look: ScreenLook) {
    if (lines.isEmpty()) return
    Column {
        BasicText(stringResource(title), Modifier.semantics { heading() }, style = look.heading)
        lines.forEachIndexed { index, line ->
            Row(
                Modifier.fillMaxWidth()
                    .then(if (index > 0) Modifier.stitchedAbove(look.palette) else Modifier)
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PegBullet(look.palette)
                BasicText(stringResource(line), Modifier.weight(1f), style = look.body)
            }
        }
    }
}

@Composable
private fun PegBullet(palette: Palette) {
    Box(
        Modifier.padding(top = PEG_BULLET_TOP)
            .size(PEG_BULLET_WIDTH, PEG_BULLET_HEIGHT)
            .clearAndSetSemantics {}
            .drawBehind { drawRoundRect(palette.pegWood, cornerRadius = CornerRadius(PEG_BULLET_CORNER.toPx())) },
    )
}

private fun Modifier.stitchedAbove(palette: Palette): Modifier = drawBehind {
    val y = STITCH_WIDTH.toPx() / 2
    drawLine(
        palette.rule,
        Offset(0f, y),
        Offset(size.width, y),
        strokeWidth = STITCH_WIDTH.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(STITCH_DASH.toPx(), STITCH_GAP.toPx())),
    )
}
