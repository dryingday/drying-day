package io.github.dryingday.forecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.advise
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.ForecastSource
import io.github.dryingday.weather.SourceError
import io.github.dryingday.weather.SourceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Automatic fetches start at this age. */
internal val FORECAST_FRESH_FOR: Duration = 1.hours

/** A forecast this old gives no advice. */
internal val FORECAST_TOO_OLD: Duration = 24.hours

/** After a failed fetch, automatic attempts wait this long. */
internal val AUTO_RETRY_AFTER: Duration = 15.minutes

private const val LOAD_KEY = "load"
private const val MILLIS_PER_MINUTE = 60_000L

/**
 * The forecast for the current place as the main screen sees it: when it is fetched, how old it can be, and the
 * advice for it. See docs/bolts/05-main-screen.md, "The forecast". Nothing is fetched unless the main screen is
 * on screen ([setOnScreen]) or the user asks ([refresh]), and only for the current place ([setPlace]).
 *
 * @param scope runs the fetch and the minute tick; the caller owns it.
 * @param settings holds the load under `load`.
 */
class ForecastState(
    private val source: ForecastSource,
    private val cache: ForecastCache,
    private val settings: Settings,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    var status: ForecastStatus by mutableStateOf(ForecastStatus.Loading)
        private set

    var load: Load by mutableStateOf(readLoad())
        private set

    private var place: Coordinates? = null
    private var onScreen = false
    private var now: Instant = clock.now()
    private var lastError: SourceError? = null
    private var retryNotBefore: Instant? = null
    private var fetchJob: Job? = null
    private var tickJob: Job? = null

    init {
        recompute()
    }

    /** Switching the load re-runs the engine and fetches nothing. */
    fun chooseLoad(value: Load) {
        load = value
        settings.putString(LOAD_KEY, value.name)
        recompute()
    }

    /** A change of place cancels a fetch, clears the wait and the last error. Null when there is no place. */
    fun setPlace(coordinates: Coordinates?) {
        if (coordinates == place) return
        place = coordinates
        cancelFetch()
        lastError = null
        retryNotBefore = null
        now = clock.now()
        recompute()
        if (onScreen) fetchIfDue()
    }

    /** On screen: the app is visible and showing the main screen. Leaving cancels a fetch and stops the tick. */
    fun setOnScreen(value: Boolean) {
        if (value == onScreen) return
        onScreen = value
        if (value) {
            tickJob = scope.launch { tickEveryMinute() }
        } else {
            tickJob?.cancel()
            tickJob = null
            cancelFetch()
            recompute()
        }
    }

    /** The Refresh button: ignores both waits, and does nothing while a fetch runs or with no place. */
    fun refresh() {
        if (fetchJob == null) startFetch()
    }

    private suspend fun tickEveryMinute() {
        while (true) {
            now = clock.now()
            recompute()
            fetchIfDue()
            delay(MILLIS_PER_MINUTE - now.toEpochMilliseconds().mod(MILLIS_PER_MINUTE))
        }
    }

    private fun fetchIfDue() {
        val coordinates = place ?: return
        if (fetchJob != null || waiting()) return
        val age = cache.get(coordinates)?.let { now - it.fetchedAt }
        if (age == null || age.isNegative() || age >= FORECAST_FRESH_FOR) startFetch()
    }

    // A wait longer than it can be means the clock was set back.
    private fun waiting(): Boolean {
        val until = retryNotBefore ?: return false
        return now < until && until - now <= AUTO_RETRY_AFTER
    }

    private fun startFetch() {
        val coordinates = place ?: return
        fetchJob = scope.launch {
            val result = source.forecast(coordinates)
            ensureActive()
            val done = clock.now()
            now = done
            when (result) {
                is SourceResult.Success -> {
                    cache.put(coordinates, done, result.value)
                    lastError = null
                }

                is SourceResult.Failure -> {
                    lastError = result.error
                    retryNotBefore = done + AUTO_RETRY_AFTER
                }
            }
            fetchJob = null
            recompute()
        }
        recompute()
    }

    private fun cancelFetch() {
        fetchJob?.cancel()
        fetchJob = null
    }

    private fun recompute() {
        val refreshing = fetchJob != null
        val entry = place?.let { cache.get(it) }
        status = if (entry == null) {
            lastError?.takeUnless { refreshing }?.let { ForecastStatus.Failed(it) } ?: ForecastStatus.Loading
        } else {
            val age = now - entry.fetchedAt
            val forecast = entry.forecast
            if (!age.isNegative() && age >= FORECAST_TOO_OLD) {
                ForecastStatus.OutOfDate(entry.fetchedAt, refreshing, lastError)
            } else {
                ForecastStatus.Showing(
                    fetchedAt = entry.fetchedAt,
                    timeZone = forecast.timeZone,
                    today = now.toLocalDateTime(forecast.timeZone).date,
                    days = advise(forecast, load, now),
                    refreshing = refreshing,
                    lastError = lastError,
                )
            }
        }
    }

    private fun readLoad(): Load = if (settings.getStringOrNull(LOAD_KEY) == Load.LIGHT.name) Load.LIGHT else Load.HEAVY
}
