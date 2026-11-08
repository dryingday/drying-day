package io.github.dryingday.engine

import io.github.dryingday.engine.DryingThresholds.COLD_BELOW_C
import io.github.dryingday.engine.DryingThresholds.SHORT_DAYLIGHT
import io.github.dryingday.engine.DryingThresholds.SLOW_VPD_KPA
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Rule 6: what held drying back on a day that isn't [Verdict.WILL_DRY], in the order of [Reason].
 *
 * [window] is the chosen window, or null when there's none. The "most of the time" reasons are judged over
 * the window, from hang-out to its end, or with no window over the day's usable time. "Most" is more than
 * half, by time. [Reason.DAY_OVER], today after a sunset, is the only reason when it applies.
 */
internal fun reasons(hours: List<HourlyWeather>, daylight: Interval, now: Instant, window: Window?): List<Reason> {
    if (daylight.length > Duration.ZERO && now >= daylight.end) return listOf(Reason.DAY_OVER)
    val day = JudgedDay(hours, daylight, now, window)
    return Reason.entries.filter { day.applies(it) }
}

private class JudgedDay(
    private val hours: List<HourlyWeather>,
    daylight: Interval,
    now: Instant,
    private val window: Window?,
) {
    private val usable = Interval(minOf(maxOf(daylight.start, now), daylight.end), daylight.end)
    private val judged = window?.let { Interval(it.hangOut, it.end) } ?: usable
    private val usableHours = hours.filter { it.usableTime(daylight, now) > Duration.ZERO }

    fun applies(reason: Reason): Boolean = when (reason) {
        Reason.DAY_OVER -> false

        Reason.RAIN -> endedByOrAnyUsable(HourKind.WET)

        Reason.FOG -> endedByOrAnyUsable(HourKind.FOG)

        Reason.COLD -> mostOfTheTime { it.isSlow() && it.temperatureC < COLD_BELOW_C }

        Reason.DAMP_AIR ->
            mostOfTheTime { it.isSlow() && it.temperatureC >= COLD_BELOW_C } || window?.endedBy == HourKind.DEW

        Reason.STILL_AIR -> mostOfTheTime { windFactor(it.windSpeedKmh) < 1.0 }

        Reason.SHORT_DAYLIGHT -> usable.length < SHORT_DAYLIGHT
    }

    private fun endedByOrAnyUsable(kind: HourKind) =
        window?.endedBy == kind || (window == null && usableHours.any { it.kind() == kind })

    private fun mostOfTheTime(condition: (HourlyWeather) -> Boolean): Boolean {
        val time = hours.filter(condition).fold(Duration.ZERO) { sum, hour -> sum + hour.overlap(judged) }
        return time * 2 > judged.length
    }
}

private fun HourlyWeather.isSlow() = vapourPressureDeficitKpa(temperatureC, relativeHumidityPercent) < SLOW_VPD_KPA

/** How much of this hour lies in [span]. */
private fun HourlyWeather.overlap(span: Interval): Duration {
    val from = maxOf(start, span.start)
    val to = minOf(start + 1.hours, span.end)
    return if (to > from) to - from else Duration.ZERO
}
