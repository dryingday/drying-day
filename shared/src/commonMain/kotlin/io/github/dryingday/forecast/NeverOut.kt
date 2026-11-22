package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import kotlinx.datetime.LocalDate

private const val WHOLE_PERCENT = 100

// "The washing was never out", one test for the rain line, the wet patch, the drips, the motion and the sky.
// See docs/bolts/09-washing-line.md, "The line and the cloths".

/**
 * Whether the washing was never out on [date]: there is no [advice] for it (a null day), its only reason is that
 * today's daylight is over, or it has no usable light (polar night). The engine gives such a day a dryness of 0,
 * which a wet cloth would misreport. It depends on nothing but the day, so a time isn't a parameter.
 */
internal fun neverOut(forecast: Forecast, date: LocalDate, advice: DayAdvice?): Boolean {
    if (advice == null) return true
    val dayOver = advice.verdict == Verdict.DONT_BOTHER && advice.reasons == listOf(Reason.DAY_OVER)
    val daylight = forecast.daylightOf(date)
    return dayOver || daylight == null || daylight.end <= daylight.start
}

/** How much of the day's washing is still wet when it comes in, in percent; null when it was never out. */
internal fun wetShare(forecast: Forecast, date: LocalDate, advice: DayAdvice?): Int? =
    if (advice == null || neverOut(forecast, date, advice)) null else WHOLE_PERCENT - advice.dryness
