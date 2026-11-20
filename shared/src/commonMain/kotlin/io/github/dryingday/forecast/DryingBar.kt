package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Verdict
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_bar_label
import io.github.dryingday.shared.resources.forecast_bar_single
import io.github.dryingday.shared.resources.forecast_dryness
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The drying window bar above the hours row, as logic: where it goes, where the row opens and what it says.
// The screen measures the widths and draws the result. See docs/bolts/08-fewer-words.md, "The drying window bar".

/** The thinnest the bar is drawn, so a short window or one whose times meet still shows. */
internal const val MIN_BAR_DP = 6f

/** The bar's start and end, in dp along the row (0 at the first column's left edge). */
internal class BarSpan(val start: Float, val end: Float)

/**
 * Where the bar goes along a row of [columns], each [columnWidth] dp wide, in a row [rowWidth] dp wide. Null where
 * there is no bar: no [window] or no columns. The window's ends are placed by time: a time falls in the column
 * whose hour holds it, at its fraction of the hour; one before the first column goes to the row's start and one
 * after the last to its end. A bar narrower than [MIN_BAR_DP] is widened about its centre, then kept in the row.
 */
internal fun barSpan(columns: List<HourColumn>, window: Window?, columnWidth: Float, rowWidth: Float): BarSpan? {
    if (window == null || columns.isEmpty()) return null
    val from = positionOf(columns, window.hangOut, columnWidth).coerceIn(0f, rowWidth)
    val to = positionOf(columns, window.bringIn, columnWidth).coerceIn(from, rowWidth)
    val width = minOf(MIN_BAR_DP, rowWidth)
    val start = if (to - from >= width) from else ((from + to) / 2 - width / 2).coerceIn(0f, rowWidth - width)
    return BarSpan(start, if (to - from >= width) to else start + width)
}

private fun positionOf(columns: List<HourColumn>, time: Instant, columnWidth: Float): Float {
    val index = columns.indexOfLast { it.start <= time }
    if (index < 0) return 0f
    val fraction = ((time - columns[index].start) / 1.hours).coerceIn(0.0, 1.0).toFloat()
    return (index + fraction) * columnWidth
}

/**
 * The column a day's row opens at: one before the bar's first column, or the first column if the bar starts there
 * or there is no bar.
 */
internal fun openingColumn(columns: List<HourColumn>, window: Window?): Int {
    if (window == null) return 0
    return (columns.indexOfLast { it.start <= window.hangOut } - 1).coerceAtLeast(0)
}

/**
 * The bar's two times as one label, "10:15 to 11:00", for when they don't fit side by side; the one time when the
 * two meet in a minute.
 */
internal fun barLabel(window: Window, zone: TimeZone, formatTime: TimeFormat): Message {
    val from = formatTime(window.hangOut, zone)
    val to = formatTime(window.bringIn, zone)
    return if (from ==
        to
    ) {
        Message(Res.string.forecast_bar_single, listOf(from))
    } else {
        Message(Res.string.forecast_bar_label, listOf(from, to))
    }
}

/** "About 70 % dry by 14:30.", on a partly dry day with times; null otherwise. */
internal fun DayAdvice.drynessLine(zone: TimeZone, formatTime: TimeFormat): Message? {
    val window = window()
    if (verdict != Verdict.PARTLY_DRY || window == null) return null
    return Message(Res.string.forecast_dryness, listOf(drynessText(), formatTime(window.bringIn, zone)))
}

/**
 * Whether "Times are in the place's own time zone." shows: when there is an hours row or written times to
 * explain, and the device's offset differs from the place's.
 */
internal fun showsZoneNote(
    columns: List<HourColumn>,
    window: Window?,
    forecastZone: TimeZone,
    deviceZone: TimeZone,
    now: Instant,
): Boolean = (columns.isNotEmpty() || window != null) && timesAreLocalToPlace(forecastZone, deviceZone, now)
