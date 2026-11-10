package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.weather.SourceError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** What the main screen shows about the forecast. Holds no coordinates. */
sealed interface ForecastStatus {
    /** Nothing usable yet, and the first fetch is running. */
    data object Loading : ForecastStatus

    /** The last automatic or requested fetch failed, and there's no usable forecast to show. */
    data class Failed(val error: SourceError) : ForecastStatus

    /**
     * A cached forecast under 24 hours old, or of unknown age. [today] is `now`'s date in [timeZone], and [days]
     * are for it and the two dates after, so a null day still has a date to label. [refreshing] while a fetch
     * runs; [lastError] if the last one failed.
     */
    data class Showing(
        val fetchedAt: Instant,
        val timeZone: TimeZone,
        val today: LocalDate,
        val days: List<DayAdvice?>,
        val refreshing: Boolean,
        val lastError: SourceError?,
    ) : ForecastStatus

    /** A cached forecast 24 hours old or more, with no fresh one yet. */
    data class OutOfDate(val fetchedAt: Instant, val refreshing: Boolean, val lastError: SourceError?) : ForecastStatus
}
