package io.github.dryingday.weather.openmeteo

import io.github.dryingday.engine.Forecast
import io.github.dryingday.weather.Attribution
import io.github.dryingday.weather.AttributionScope
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.ForecastSource
import io.github.dryingday.weather.Place
import io.github.dryingday.weather.PlaceSearchSource
import io.github.dryingday.weather.SourceError
import io.github.dryingday.weather.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.round

private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
private const val SEARCH_URL = "https://geocoding-api.open-meteo.com/v1/search"
private const val HOURLY_FIELDS =
    "temperature_2m,relative_humidity_2m,wind_speed_10m,visibility,precipitation_probability,precipitation," +
        "wind_gusts_10m,shortwave_radiation"
private const val DAILY_FIELDS = "sunrise,sunset,daylight_duration"
private const val DAYS_BEFORE_TODAY = 1
private const val FORECAST_DAYS = 5
private const val MIN_QUERY_LENGTH = 2 // The API returns nothing for one character.
private const val PLACE_RESULTS = 10
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val COORDINATE_SCALE = 100.0

/**
 * Open-Meteo. See docs/bolts/03-weather-source.md. The client is built here, not passed in, so the timeouts and
 * the no-redirects rule apply whoever uses this; there's deliberately no logging plugin, since URLs hold coordinates.
 * Keep one for the life of the app, with its own engine: the client is never closed.
 */
class OpenMeteoSource(engine: HttpClientEngine) :
    ForecastSource,
    PlaceSearchSource {
    override val id = "open-meteo"
    override val attributions =
        listOf(
            Attribution(
                name = "Open-Meteo.com",
                url = "https://open-meteo.com/",
                licence = "CC BY 4.0",
                licenceUrl = "https://creativecommons.org/licenses/by/4.0/",
                covers = AttributionScope.WEATHER,
            ),
            Attribution(
                name = "GeoNames",
                url = "https://www.geonames.org/",
                licence = "CC BY 4.0",
                licenceUrl = "https://creativecommons.org/licenses/by/4.0/",
                covers = AttributionScope.PLACES,
            ),
        )

    private val client =
        HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }

    override suspend fun forecast(at: Coordinates): SourceResult<Forecast> {
        val rounded = roundedForRequest(at)
        val parameters =
            listOf(
                "latitude" to rounded.latitude,
                "longitude" to rounded.longitude,
                "hourly" to HOURLY_FIELDS,
                "daily" to DAILY_FIELDS,
                "timezone" to "auto",
                "timeformat" to "unixtime",
                "past_days" to DAYS_BEFORE_TODAY,
                "forecast_days" to FORECAST_DAYS,
            )
        return fetch(FORECAST_URL, parameters, ::parseForecast)
    }

    override suspend fun searchPlaces(query: String, language: String?): SourceResult<List<Place>> {
        val name = query.trim()
        if (name.length < MIN_QUERY_LENGTH) return SourceResult.Success(emptyList())
        val parameters =
            listOfNotNull(
                "name" to name,
                "count" to PLACE_RESULTS,
                "format" to "json",
                language?.takeIf { it.isNotBlank() }?.let { "language" to it },
            )
        return fetch(SEARCH_URL, parameters, ::parsePlaces)
    }

    /** Every failure becomes a code: exceptions here can hold the URL, and so the coordinates. */
    @Suppress("TooGenericExceptionCaught") // "Any other exception" is the spec: nothing may escape with a URL in it.
    private suspend fun <T : Any> fetch(
        url: String,
        parameters: List<Pair<String, Any>>,
        parse: (String) -> T?,
    ): SourceResult<T> = try {
        val response =
            client.get(url) {
                parameters.forEach { (name, value) -> parameter(name, value) }
            }
        if (response.status.isSuccess()) {
            // As bytes, since JSON is UTF-8: a bad Content-Type mustn't make a complete reply look like a lost one.
            parse(response.bodyAsBytes().decodeToString())?.let { SourceResult.Success(it) }
                ?: SourceResult.Failure(SourceError.BAD_RESPONSE)
        } else {
            SourceResult.Failure(SourceError.SERVICE)
        }
    } catch (_: Exception) {
        // If the caller was cancelled, that rethrows, whatever the engine reported the abort as. Any other
        // cancellation is the request's own, such as a timeout; letting it out would silently end a running caller.
        currentCoroutineContext().ensureActive()
        SourceResult.Failure(SourceError.UNREACHABLE)
    }
}

/** About a kilometre, and never more precise, whatever the caller has. Adding 0.0 turns -0.0 into 0.0. */
internal fun roundedForRequest(at: Coordinates) = Coordinates(
    latitude = round(at.latitude * COORDINATE_SCALE) / COORDINATE_SCALE + 0.0,
    longitude = round(at.longitude * COORDINATE_SCALE) / COORDINATE_SCALE + 0.0,
)
