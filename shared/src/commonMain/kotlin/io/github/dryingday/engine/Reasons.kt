package io.github.dryingday.engine

import io.github.dryingday.engine.DryingThresholds.COLD_BELOW_C
import io.github.dryingday.engine.DryingThresholds.MOST_SHARE_DIVISOR
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
 *
 * Rule 7: when that gives an empty list and [weakestFactor] is set, the one reason whose factor came closest
 * to its threshold is added; see [JudgedDay.weakest].
 */
@Suppress("LongParameterList") // Rule 6 inputs, plus the switch the tests use to see the table alone.
internal fun reasons(
    hours: List<HourlyWeather>,
    daylight: Interval,
    usable: Interval,
    now: Instant,
    window: Window?,
    weakestFactor: Boolean = true,
): List<Reason> {
    if (daylight.length > Duration.ZERO && now >= daylight.end) return listOf(Reason.DAY_OVER)
    val day = JudgedDay(hours, daylight, usable, now, window)
    val table = Reason.entries.filter { day.applies(it) }
    return if (table.isEmpty() && weakestFactor) listOfNotNull(day.weakest()) else table
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

    /**
     * Rule 7: the reason, of COLD, DAMP_AIR, STILL_AIR and SHORT_DAYLIGHT, whose closeness to its threshold is
     * highest, the first in that order on a tie. Closenesses are fractions of whole milliseconds, compared by
     * cross-multiplying, so a tie is exact. Null only when there is no time to judge.
     */
    fun weakest(): Reason? {
        val span = judged.length.inWholeMilliseconds
        val usableMs = usable.length.inWholeMilliseconds
        if (span <= 0 || usableMs <= 0) return null
        val closeness = listOf(
            Reason.COLD to share(span) { it.isSlow() && it.temperatureC < COLD_BELOW_C },
            Reason.DAMP_AIR to share(span) { it.isSlow() && it.temperatureC >= COLD_BELOW_C },
            Reason.STILL_AIR to share(span) { windFactor(it.windSpeedKmh) < STILL_AIR_BELOW_FACTOR },
            Reason.SHORT_DAYLIGHT to Fraction(SHORT_DAYLIGHT.inWholeMilliseconds, usableMs),
        )
        return closeness.reduce { best, next -> if (next.second > best.second) next else best }.first
    }

    // The share of the judged time that meets the condition, divided by the share that is "most".
    private fun share(span: Long, condition: (HourlyWeather) -> Boolean) =
        Fraction(hours.timeIn(judged, condition).inWholeMilliseconds * MOST_SHARE_DIVISOR, span)

    private fun heldBack(kind: HourKind) = window?.endedBy == kind || before.any { it.kind() == kind }

    private fun mostOfTheTime(condition: (HourlyWeather) -> Boolean) = hours.mostOf(judged, condition)
}

/** [numerator] over [denominator], whole milliseconds, compared by cross-multiplying without rounding. */
private class Fraction(val numerator: Long, val denominator: Long) : Comparable<Fraction> {
    override fun compareTo(other: Fraction) = (numerator * other.denominator).compareTo(other.numerator * denominator)
}

private fun HourlyWeather.isSlow() = vapourPressureDeficitKpa(temperatureC, relativeHumidityPercent) < SLOW_VPD_KPA
