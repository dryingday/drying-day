package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Verdict
import io.github.dryingday.engine.firstWet
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// Which sky a day gets, as logic. The palette owns the colours and the screen maps a kind to them.
// See docs/bolts/09-washing-line.md, "The main screen".

/** The picture in the sky, drawn on the right below the place's row. */
internal enum class SkyPicture { SUN, SUN_BEHIND_CLOUD, RAIN_CLOUD, NONE }

/** Which of the three skies. A day with no advice gets [PARTLY_DRY]'s. */
internal enum class SkyKind { WILL_DRY, PARTLY_DRY, DONT_BOTHER }

/** A day's sky: its [kind], its [picture] and whether [rain] falls in front of it. */
internal data class SkyChoice(val kind: SkyKind, val picture: SkyPicture, val rain: Boolean)

/** The sky when there's no usable forecast at all: the partly dry colour and no picture. */
internal val NO_FORECAST_SKY = SkyChoice(SkyKind.PARTLY_DRY, SkyPicture.NONE, rain = false)

/**
 * The sky for [date]. A null day has the partly dry sky and no picture. Rain falls only on a don't-bother day
 * whose first wet hour exists, never on a day the washing was never out (today after sunset, polar night).
 */
internal fun skyChoice(forecast: Forecast, date: LocalDate, advice: DayAdvice?, now: Instant): SkyChoice {
    if (advice == null) return NO_FORECAST_SKY
    return when (advice.verdict) {
        Verdict.WILL_DRY -> SkyChoice(SkyKind.WILL_DRY, SkyPicture.SUN, rain = false)

        Verdict.PARTLY_DRY -> SkyChoice(SkyKind.PARTLY_DRY, SkyPicture.SUN_BEHIND_CLOUD, rain = false)

        Verdict.DONT_BOTHER -> SkyChoice(
            SkyKind.DONT_BOTHER,
            SkyPicture.RAIN_CLOUD,
            rain = !neverOut(forecast, date, advice) && firstWet(forecast, date, now) != null,
        )
    }
}
