package io.github.dryingday.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Drying advice for today, tomorrow and the day after, as local dates in the forecast's zone, for [load]
 * asked at [now]. A day is null when its forecast is incomplete: no daylight data, or an hour of its
 * daylight from [now] onwards missing. Rules 3 to 5 of docs/bolts/02-drying-rules.md.
 */
fun advise(forecast: Forecast, load: Load, now: Instant): List<DayAdvice?> {
    val today = now.toLocalDateTime(forecast.timeZone).date
    return (0 until DAYS).map { adviseDay(forecast, load, now, today.plus(it, DateTimeUnit.DAY)) }
}

private fun adviseDay(forecast: Forecast, load: Load, now: Instant, date: LocalDate): DayAdvice? {
    val light = forecast.days.firstOrNull { it.date == date } ?: return null
    val daylight = light.daylight(forecast.timeZone)
    val from = maxOf(daylight.start, now)
    val hours = forecast.hours.filter { it.start < daylight.end && it.start + 1.hours > from }
    val hasUsableTime = from < daylight.end
    return when {
        hasUsableTime && !covers(hours, Interval(from, daylight.end)) -> null

        else -> fromBestWindow(
            date,
            if (hasUsableTime) windows(hours, daylight, now, load.needPoints) else emptyList(),
            load.needPoints,
        ) { best -> reasons(hours, daylight, now, best) }
    }
}

/**
 * Rules 4 and 5: the window with the earliest bring-in among those that dry the load, else the one with the
 * most points (a tie goes to the earlier). No window at all is DONT_BOTHER at 0 %.
 */
private fun fromBestWindow(
    date: LocalDate,
    windows: List<Window>,
    need: Double,
    reasons: (Window?) -> List<Reason>,
): DayAdvice {
    val best = windows.filter { it.dry }.minByOrNull { it.bringIn } ?: windows.maxByOrNull { it.points }
    val dryness = when {
        best == null -> 0

        best.dry -> MAX_DRYNESS

        // Rounded down, but a point sum a hair under a whole percentage still counts as reaching it.
        else -> minOf(floor(best.points / need * MAX_DRYNESS + PERCENT_TOLERANCE).toInt(), MAX_DRYNESS - 1)
    }
    val verdict = when {
        best?.dry == true -> Verdict.WILL_DRY
        dryness >= DryingThresholds.PARTLY_DRY_FROM_PERCENT -> Verdict.PARTLY_DRY
        else -> Verdict.DONT_BOTHER
    }
    val shown = verdict != Verdict.DONT_BOTHER
    return DayAdvice(
        date = date,
        verdict = verdict,
        hangOut = best?.hangOut.takeIf { shown },
        bringIn = best?.bringIn.takeIf { shown },
        dryness = dryness,
        reasons = if (verdict == Verdict.WILL_DRY) emptyList() else reasons(best),
        tips = emptyList(),
        indoorPlan = emptyList(),
    )
}

/**
 * A stretch of drying time on the line: from [hangOut] to [bringIn], earning [points]. The window itself
 * runs on to [end], which [endedBy] (a wet or damp hour) or sunset, when [endedBy] is null, brought about.
 */
internal class Window(
    val hangOut: Instant,
    val bringIn: Instant,
    val end: Instant,
    val endedBy: HourKind?,
    val points: Double,
    val dry: Boolean,
)

/**
 * Rule 4. A window is a run of drying hours: a wet or damp hour ends it. It starts at the usable part of
 * its first hour that earns points, and ends when the load reaches [need] or at the window's end.
 */
private fun windows(hours: List<HourlyWeather>, daylight: Interval, now: Instant, need: Double): List<Window> {
    val windows = mutableListOf<Window>()
    var hangOut: Instant? = null
    var end: Instant = daylight.start
    var points = 0.0
    var dryAt: Instant? = null

    fun close(endedBy: HourKind?) {
        hangOut?.let { windows += Window(it, dryAt ?: end, end, endedBy, minOf(points, need), dryAt != null) }
        hangOut = null
        points = 0.0
        dryAt = null
    }

    fun add(hour: HourlyWeather, usable: Duration) {
        val usableFrom = maxOf(hour.start, daylight.start, now)
        val rate = hour.pointsPerHour()
        if (hangOut == null && rate > 0.0) hangOut = usableFrom
        if (hangOut != null && dryAt == null) {
            val earned = rate * (usable / 1.hours)
            // Point sums are doubles: twenty hours at 0.3 come to 5.999999999999998, which is 6.
            if (points + earned >= need - POINTS_TOLERANCE) {
                dryAt = usableFrom + 1.hours * (maxOf(need - points, 0.0) / rate)
                points = need
            } else {
                points += earned
            }
        }
        end = usableFrom + usable
    }

    for (hour in hours) {
        val usable = hour.usableTime(daylight, now)
        when {
            usable <= Duration.ZERO -> Unit
            hour.kind() != HourKind.DRYING -> close(hour.kind())
            else -> add(hour, usable)
        }
    }
    close(endedBy = null)
    return windows
}

/** Whether [hours], in order, cover [span] with no gap. */
private fun covers(hours: List<HourlyWeather>, span: Interval): Boolean {
    if (hours.isEmpty() || hours.first().start > span.start || hours.last().start + 1.hours < span.end) return false
    return hours.zipWithNext().all { (a, b) -> b.start == a.start + 1.hours }
}

private const val DAYS = 3
private const val MAX_DRYNESS = 100
private const val POINTS_TOLERANCE = 1e-9
private const val PERCENT_TOLERANCE = 1e-6
