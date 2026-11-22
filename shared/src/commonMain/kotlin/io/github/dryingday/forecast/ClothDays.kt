package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_day_missing
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

// What each of the three cloths on the line shows and does, as logic. The screen draws it.
// See docs/bolts/09-washing-line.md, "The line and the cloths".

/** The cloths on the line: today, tomorrow and the day after. */
internal const val CLOTH_DAYS = 3

/**
 * One cloth: its [shortName] small and [fullName] open, its [advice] (null for a null day), the verdict it prints
 * open ([headline], null for a null day), the [sentence] under it, how much of it is wet ([wetShare] in percent,
 * null when the washing was never out) and how it moves.
 */
@Suppress("LongParameterList") // What one cloth shows, each by name.
internal class ClothDay(
    val index: Int,
    val shortName: StringResource,
    val fullName: StringResource,
    val advice: DayAdvice?,
    val headline: StringResource?,
    val sentence: List<Message>,
    val wetShare: Int?,
    val motion: Motion,
)

/** The three cloths for a forecast that's showing, from [now]. */
internal fun clothDays(
    status: ForecastStatus.Showing,
    now: Instant,
    animationsOn: Boolean,
    formatTime: TimeFormat,
): List<ClothDay> = List(CLOTH_DAYS) { index ->
    val advice = status.days.getOrNull(index)
    val date = status.today.plus(index, DateTimeUnit.DAY)
    ClothDay(
        index = index,
        shortName = clothShortName(status.today, index),
        fullName = dayLabel(status.today, index),
        advice = advice,
        headline = advice?.headline(),
        sentence = clothSentence(status.forecast, date, advice, now, formatTime),
        wetShare = wetShare(status.forecast, date, advice),
        motion = motion(advice?.verdict, neverOut(status.forecast, date, advice), animationsOn),
    )
}

/**
 * The open cloth's sentence: on a partly dry day the dryness line, then the rain line; otherwise the rain line.
 * None where there's no rain line (today after sunset, polar night). A null day says there isn't enough forecast.
 */
internal fun clothSentence(
    forecast: Forecast,
    date: LocalDate,
    advice: DayAdvice?,
    now: Instant,
    formatTime: TimeFormat,
): List<Message> {
    if (advice == null) return listOf(Message(Res.string.forecast_day_missing))
    return listOfNotNull(
        advice.drynessLine(forecast.timeZone, formatTime),
        rainLine(forecast, date, advice, now, formatTime),
    )
}
