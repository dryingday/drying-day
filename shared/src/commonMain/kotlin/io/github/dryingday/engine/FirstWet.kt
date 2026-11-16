package io.github.dryingday.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The start of the usable part of the first [HourKind.WET] hour of [date], in its usable time (rule 1:
 * daylight, and from [now] on today), or null when there is none. Also null for a date outside today to
 * today + 2 in the forecast's zone, and for a day [advise] gives null for: both decide that in [usableDay].
 * It adds no rule: it isn't the RAIN reason, which counts only wet hours up to the end of the chosen window.
 */
fun firstWet(forecast: Forecast, date: LocalDate, now: Instant): Instant? {
    val today = now.toLocalDateTime(forecast.timeZone).date
    val inRange = date >= today && date <= today.plus(LAST_DAY_AHEAD, DateTimeUnit.DAY)
    val day = if (inRange) usableDay(forecast, now, date) else null
    val wet = day?.hours?.firstOrNull {
        it.kind() == HourKind.WET && it.usableTime(day.daylight, now) > Duration.ZERO
    }
    return wet?.let { maxOf(it.start, day.daylight.start, now) }
}

private const val LAST_DAY_AHEAD = 2
