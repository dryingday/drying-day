package io.github.dryingday.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Sunrise to sunset, clipped to the local date in [zone]. That covers polar day (midnight to midnight),
 * polar night (sunrise equal to sunset) and a sunset after midnight, and gives clock-change days their
 * real length.
 */
internal fun DailyLight.daylight(zone: TimeZone): Interval {
    val dayStart = date.atStartOfDayIn(zone)
    val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    val from = minOf(maxOf(sunrise, dayStart), dayEnd)
    val to = minOf(sunset, dayEnd)
    return Interval(from, maxOf(from, to))
}

/** How long, within [span], the hours that meet [condition] last. */
internal fun List<HourlyWeather>.timeIn(span: Interval, condition: (HourlyWeather) -> Boolean): Duration =
    filter(condition).fold(Duration.ZERO) { sum, hour -> sum + hour.usableTime(span, span.start) }

/** Whether the hours that meet [condition] last more than half of [span]: "most of the time". */
internal fun List<HourlyWeather>.mostOf(span: Interval, condition: (HourlyWeather) -> Boolean): Boolean =
    timeIn(span, condition) * 2 > span.length

/** Rule 1: the part of this hour that lies in [daylight] and after [now]. */
internal fun HourlyWeather.usableTime(daylight: Interval, now: Instant): Duration {
    val from = maxOf(start, daylight.start, now)
    val to = minOf(start + 1.hours, daylight.end)
    return if (to > from) to - from else Duration.ZERO
}
