package io.github.dryingday.engine

import io.github.dryingday.engine.DryingThresholds.COLD_BELOW_C
import io.github.dryingday.engine.DryingThresholds.SHORT_DAYLIGHT
import io.github.dryingday.engine.DryingThresholds.SLOW_VPD_KPA
import io.github.dryingday.engine.DryingThresholds.STILL_AIR_BELOW_FACTOR
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Rule 6: what held drying back on a day that isn't [Verdict.WILL_DRY], in the order of [Reason].
 *
 * [usable] is the day's usable time, from [now] today, and [window] the chosen window, or null when
 * there's none. Rain, fog and dew count when one ends the window or comes before it in the usable time
 * (anywhere in it, with no window). The "most of the time" reasons are judged over the whole window,
 * including drying time before hang-out that earned nothing, or with no window over the usable time;
 * "most" is more than half, by time. [Reason.DAY_OVER], today after a sunset, is the only reason when it
 * applies.
 */
internal fun reasons(
    hours: List<HourlyWeather>,
    daylight: Interval,
    usable: Interval,
    now: Instant,
    window: Window?,
): List<Reason> {
    if (daylight.length > Duration.ZERO && now >= daylight.end) return listOf(Reason.DAY_OVER)
    val day = JudgedDay(hours, daylight, usable, now, window)
    return Reason.entries.filter { day.applies(it) }
}

private class JudgedDay(
    private val hours: List<HourlyWeather>,
    daylight: Interval,
    private val usable: Interval,
    now: Instant,
    private val window: Window?,
) {
    private val judged = window?.let { Interval(it.start, it.end) } ?: usable

    // The usable hours before the window starts, or all of them with no window.
    private val before = hours.filter {
        it.usableTime(daylight, now) > Duration.ZERO && (window == null || it.start < window.start)
    }

    fun applies(reason: Reason): Boolean = when (reason) {
        Reason.DAY_OVER -> false

        // Decided before the others, in reasons().
        Reason.RAIN -> heldBack(HourKind.WET)

        Reason.FOG -> heldBack(HourKind.FOG)

        Reason.COLD -> mostOfTheTime { it.isSlow() && it.temperatureC < COLD_BELOW_C }

        Reason.DAMP_AIR -> mostOfTheTime { it.isSlow() && it.temperatureC >= COLD_BELOW_C } || heldBack(HourKind.DEW)

        Reason.STILL_AIR -> mostOfTheTime { windFactor(it.windSpeedKmh) < STILL_AIR_BELOW_FACTOR }

        Reason.SHORT_DAYLIGHT -> usable.length < SHORT_DAYLIGHT
    }

    private fun heldBack(kind: HourKind) = window?.endedBy == kind || before.any { it.kind() == kind }

    private fun mostOfTheTime(condition: (HourlyWeather) -> Boolean) = hours.mostOf(judged, condition)
}

private fun HourlyWeather.isSlow() = vapourPressureDeficitKpa(temperatureC, relativeHumidityPercent) < SLOW_VPD_KPA
