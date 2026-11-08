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
 * daylight from [now] onwards missing. Rules 3 to 6 and the tips and indoor plan of
 * docs/bolts/02-drying-rules.md.
 */
fun advise(forecast: Forecast, load: Load, now: Instant): List<DayAdvice?> {
    val today = now.toLocalDateTime(forecast.timeZone).date
    return (0 until DAYS).map { adviseDay(forecast, load, now, today.plus(it, DateTimeUnit.DAY)) }
}

private fun adviseDay(forecast: Forecast, load: Load, now: Instant, date: LocalDate): DayAdvice? {
    val light = forecast.days.firstOrNull { it.date == date } ?: return null
    val daylight = light.daylight(forecast.timeZone)
    val usable = Interval(minOf(maxOf(daylight.start, now), daylight.end), daylight.end)
    val hours = forecast.hours.filter { it.start < daylight.end && it.start + 1.hours > usable.start }
    val complete = usable.length == Duration.ZERO || covers(hours, usable)
    return if (complete) dayAdvice(forecast, load, now, date, daylight, usable, hours) else null
}

@Suppress("LongParameterList")
private fun dayAdvice(
    forecast: Forecast,
    load: Load,
    now: Instant,
    date: LocalDate,
    daylight: Interval,
    usable: Interval,
    hours: List<HourlyWeather>,
): DayAdvice {
    val windows = if (usable.length > Duration.ZERO) windows(hours, daylight, now, load.needPoints) else emptyList()
    val (best, dryness, verdict) = outcome(windows, load.needPoints)
    // The window the washing goes out in; none when it isn't worth it.
    val onTheLine = best?.takeIf { verdict != Verdict.DONT_BOTHER }
    return DayAdvice(
        date = date,
        verdict = verdict,
        hangOut = onTheLine?.hangOut,
        bringIn = onTheLine?.bringIn,
        dryness = dryness,
        reasons = if (verdict == Verdict.WILL_DRY) emptyList() else reasons(hours, daylight, usable, now, best),
        tips = onTheLine?.let { tips(hours, it, load) }.orEmpty(),
        indoorPlan = if (verdict == Verdict.WILL_DRY) {
            emptyList()
        } else {
            // After bring-in for a partly dry load; the usable time when it isn't worth hanging out.
            indoorPlan(forecast.hours, onTheLine?.let { Interval(it.bringIn, daylight.end) } ?: usable, now, load)
        },
    )
}

private data class Outcome(val best: Window?, val dryness: Int, val verdict: Verdict)

/**
 * Rules 4 and 5: the window with the earliest bring-in among those that dry the load, else the one with the
 * most points (a tie goes to the earlier). No window at all is DONT_BOTHER at 0 %.
 */
private fun outcome(windows: List<Window>, need: Double): Outcome {
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
    return Outcome(best, dryness, verdict)
}

/**
 * A run of drying time from [start] to [end], which [endedBy] (a wet or damp hour) or sunset, when [endedBy]
 * is null, brought about. On the line from [hangOut], its first moment that earns points, to [bringIn],
 * earning [points].
 */
internal data class Window(
    val start: Instant,
    val hangOut: Instant,
    val bringIn: Instant,
    val end: Instant,
    val endedBy: HourKind?,
    val points: Double,
    val dry: Boolean,
)

/**
 * Rule 4. A window is a run of drying hours: a wet or damp hour ends it. It's on the line from the usable part
 * of its first hour that earns points, until the load reaches [need] or the window ends.
 */
private fun windows(hours: List<HourlyWeather>, daylight: Interval, now: Instant, need: Double): List<Window> {
    val builder = WindowBuilder(need)
    for (hour in hours) {
        val usable = hour.usableTime(daylight, now)
        val kind = hour.kind()
        when {
            usable <= Duration.ZERO -> Unit
            kind != HourKind.DRYING -> builder.close(kind)
            else -> builder.add(hour, maxOf(hour.start, daylight.start, now), usable)
        }
    }
    builder.close(endedBy = null)
    return builder.windows
}

private class WindowBuilder(private val need: Double) {
    val windows = mutableListOf<Window>()
    private var start: Instant? = null
    private var hangOut: Instant? = null
    private var end: Instant? = null
    private var points = 0.0
    private var dryAt: Instant? = null

    fun add(hour: HourlyWeather, usableFrom: Instant, usable: Duration) {
        val rate = hour.pointsPerHour()
        if (start == null) start = usableFrom
        if (hangOut == null && rate > 0.0) hangOut = usableFrom
        if (hangOut != null && dryAt == null) earn(rate, usableFrom, usable)
        end = usableFrom + usable
    }

    private fun earn(rate: Double, usableFrom: Instant, usable: Duration) {
        val earned = rate * (usable / 1.hours)
        // Point sums are doubles: twenty hours at 0.3 come to 5.999999999999998, which is 6.
        if (points + earned >= need - POINTS_TOLERANCE) {
            dryAt = usableFrom + 1.hours * (maxOf(need - points, 0.0) / rate)
            points = need
        } else {
            points += earned
        }
    }

    fun close(endedBy: HourKind?) {
        val (s, h, e) = Triple(start, hangOut, end)
        if (s != null && h != null && e != null) {
            windows += Window(s, h, dryAt ?: e, e, endedBy, minOf(points, need), dryAt != null)
        }
        start = null
        hangOut = null
        end = null
        points = 0.0
        dryAt = null
    }
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
