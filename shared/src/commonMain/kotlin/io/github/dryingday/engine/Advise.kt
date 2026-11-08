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
    return when {
        from >= daylight.end -> noWindow(date)
        !covers(hours, Interval(from, daylight.end)) -> null
        else -> fromBestWindow(date, windows(hours, daylight, now, load.needPoints), load.needPoints)
    }
}

/** Rules 4 and 5: the window with the earliest bring-in among those that dry the load, else the most points. */
private fun fromBestWindow(date: LocalDate, windows: List<Window>, need: Double): DayAdvice {
    val best = windows.filter { it.dry }.minByOrNull { it.bringIn }
        ?: windows.maxByOrNull { it.points }
        ?: return noWindow(date)
    val dryness = if (best.dry) MAX_DRYNESS else floor(best.points / need * MAX_DRYNESS).toInt()
    val verdict = when {
        best.dry -> Verdict.WILL_DRY
        dryness >= DryingThresholds.PARTLY_DRY_FROM_PERCENT -> Verdict.PARTLY_DRY
        else -> Verdict.DONT_BOTHER
    }
    val shown = verdict != Verdict.DONT_BOTHER
    return DayAdvice(
        date = date,
        verdict = verdict,
        hangOut = best.hangOut.takeIf { shown },
        bringIn = best.bringIn.takeIf { shown },
        dryness = dryness,
        reasons = emptyList(),
        tips = emptyList(),
        indoorPlan = emptyList(),
    )
}

/** A stretch of drying time on the line: from [hangOut] to [bringIn], earning [points]. */
private class Window(val hangOut: Instant, val bringIn: Instant, val points: Double, val dry: Boolean)

/**
 * Rule 4. A window is a run of drying hours: a wet or damp hour ends it. It starts at the usable part of
 * its first hour that earns points, and ends when the load reaches [need] or at the window's end.
 */
private fun windows(hours: List<HourlyWeather>, daylight: Interval, now: Instant, need: Double): List<Window> {
    val windows = mutableListOf<Window>()
    var hangOut: Instant? = null
    var points = 0.0
    var end: Instant? = null
    var dryAt: Instant? = null

    fun close() {
        val start = hangOut ?: return
        windows += Window(start, dryAt ?: end!!, minOf(points, need), dryAt != null)
        hangOut = null
        points = 0.0
        dryAt = null
    }

    fun add(hour: HourlyWeather, usable: Duration) {
        val usableFrom = maxOf(hour.start, daylight.start, now)
        val rate = hour.pointsPerHour()
        if (hangOut == null && rate > 0.0) hangOut = usableFrom
        if (hangOut != null && dryAt == null) {
            val earned = hour.points(usable)
            if (points + earned >= need) {
                dryAt = usableFrom + 1.hours * ((need - points) / rate)
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
            hour.kind() != HourKind.DRYING -> close()
            else -> add(hour, usable)
        }
    }
    close()
    return windows
}

/** Whether [hours], in order, cover [span] with no gap. */
private fun covers(hours: List<HourlyWeather>, span: Interval): Boolean {
    if (hours.isEmpty() || hours.first().start > span.start || hours.last().start + 1.hours < span.end) return false
    return hours.zipWithNext().all { (a, b) -> b.start == a.start + 1.hours }
}

private fun noWindow(date: LocalDate) = DayAdvice(
    date = date,
    verdict = Verdict.DONT_BOTHER,
    hangOut = null,
    bringIn = null,
    dryness = 0,
    reasons = emptyList(),
    tips = emptyList(),
    indoorPlan = emptyList(),
)

private const val DAYS = 3
private const val MAX_DRYNESS = 100
