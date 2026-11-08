package io.github.dryingday.weather

import io.github.dryingday.engine.Forecast

/**
 * A place weather data comes from. What a source can do is given by the feature interfaces it implements, so a
 * new source only implements what it offers. See docs/bolts/03-weather-source.md.
 *
 * @property id stable, for saving which source is in use.
 * @property attributions the credits its data needs, shown wherever that data is.
 */
interface WeatherSource {
    val id: String
    val attributions: List<Attribution>
}

/** A source of forecasts. */
interface ForecastSource : WeatherSource {
    suspend fun forecast(at: Coordinates): SourceResult<Forecast>
}

/** A source that can find places by name. */
interface PlaceSearchSource : WeatherSource {
    /** @param language a two-letter code for the names returned, or null for the source's default. */
    suspend fun searchPlaces(query: String, language: String?): SourceResult<List<Place>>
}

/** A point on the Earth, in degrees. */
data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        // The values stay out of the messages: coordinates must not reach an error or a log. NaN fails both.
        require(latitude in -MAX_LATITUDE..MAX_LATITUDE) { "latitude out of range" }
        require(longitude in -MAX_LONGITUDE..MAX_LONGITUDE) { "longitude out of range" }
    }

    /** Leaves the values out, for the same reason. [Place] and results that hold one inherit this. */
    override fun toString() = "Coordinates(hidden)"
}

/** A place found by name. [region] is a state, county or similar. */
data class Place(val name: String, val region: String?, val country: String?, val coordinates: Coordinates)

/** A credit a source's licence asks for. The words around these go in string resources. */
data class Attribution(
    val name: String,
    val url: String,
    val licence: String,
    val licenceUrl: String,
    val covers: AttributionScope,
)

/** The kind of data an [Attribution] credits. */
enum class AttributionScope { WEATHER, PLACES }

/** The outcome of asking a source for something. */
sealed interface SourceResult<out T> {
    data class Success<out T>(val value: T) : SourceResult<T>

    /** Only a code: the exceptions behind a failure can hold the request's URL, and so its coordinates. */
    data class Failure(val error: SourceError) : SourceResult<Nothing>
}

/** Why a source couldn't answer. */
enum class SourceError {
    /**
     * Offline, DNS, TLS, a timeout, a connection lost while reading the reply, or any other exception that
     * isn't from decoding or mapping a complete reply.
     */
    UNREACHABLE,

    /** The source answered with a status other than 2xx. */
    SERVICE,

    /** A complete 2xx reply that couldn't be decoded or mapped. */
    BAD_RESPONSE,
}

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0
