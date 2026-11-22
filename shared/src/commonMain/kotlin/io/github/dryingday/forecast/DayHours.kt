package io.github.dryingday.forecast

import io.github.dryingday.engine.Condition
import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Interval
import io.github.dryingday.engine.daylight
import io.github.dryingday.engine.firstWet
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_no_rain
import io.github.dryingday.shared.resources.forecast_rain_from
import io.github.dryingday.shared.resources.hour_in_drying_window
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The selected day's hours and rain line, as the screen shows them. Pure logic: the screen draws the result.
// See docs/bolts/07-weather-at-a-glance.md, "The screen" and "Accessibility".

/** A string resource and the arguments to fill it with, so a test can check text without resolving it. */
internal class Message(val resource: StringResource, val args: List<String> = emptyList())

/**
 * One column of the hours row. [time] is the hour's start in the forecast's zone, through the device's hour
 * format ("8 AM" or "08"). [inDryingWindow] puts the bar under the column.
 */
internal class HourColumn(val start: Instant, val time: String, val condition: Condition?, val inDryingWindow: Boolean)

private const val SPOKEN_SEPARATOR = ", "

/**
 * The columns for [date]: every forecast hour that overlaps the day's daylight and, today, ends after [now].
 * None for a null [advice], for a day with no usable light (polar night, or [now] after sunset) and where the
 * forecast has a gap. [advice] is the day's, from the same [now].
 */
internal fun dayHours(
    forecast: Forecast,
    date: LocalDate,
    advice: DayAdvice?,
    now: Instant,
    formatHour: TimeFormat,
): List<HourColumn> {
    val daylight = advice?.let { forecast.daylightOf(date) }?.takeIf { it.end > it.start && it.end > now }
    if (advice == null || daylight == null) return emptyList()
    return forecast.hours
        .filter { it.start < daylight.end && it.start + 1.hours > maxOf(daylight.start, now) }
        .map {
            HourColumn(
                start = it.start,
                time = formatHour(it.start, forecast.timeZone),
                condition = it.condition,
                inDryingWindow = advice.inWindow(it.start),
            )
        }
}

internal fun Forecast.daylightOf(date: LocalDate): Interval? = days.firstOrNull { it.date == date }?.daylight(timeZone)

private fun DayAdvice.inWindow(start: Instant): Boolean {
    val from = hangOut
    val to = bringIn
    return from != null && to != null && start < to && start + 1.hours > from
}

/**
 * The rain line: when rain is due, or that none is expected while it's light. Null where the line is left out:
 * for a null day, a day with no usable light, and a day whose only reason is that daylight is over.
 */
internal fun rainLine(
    forecast: Forecast,
    date: LocalDate,
    advice: DayAdvice?,
    now: Instant,
    formatTime: TimeFormat,
): Message? {
    if (neverOut(forecast, date, advice)) return null
    val wet = firstWet(forecast, date, now)
    return if (wet == null) {
        Message(Res.string.forecast_no_rain)
    } else {
        Message(Res.string.forecast_rain_from, listOf(formatTime(wet.roundedDown(), forecast.timeZone)))
    }
}

/**
 * What a screen reader says for a column, in parts: the [time], then only the parts the column shows. Join
 * them with [spokenText] once each has been resolved to text.
 */
internal fun HourColumn.spokenParts(): List<Message> = buildList {
    condition?.let { add(Message(it.spokenName())) }
    if (inDryingWindow) add(Message(Res.string.hour_in_drying_window))
}

internal fun HourColumn.spokenText(resolve: (Message) -> String): String =
    (listOf(time) + spokenParts().map(resolve)).joinToString(SPOKEN_SEPARATOR)
