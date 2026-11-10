package io.github.dryingday.forecast

import com.russhwolf.settings.Settings
import io.github.dryingday.engine.DailyLight
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.HourlyWeather
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.rounded
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Instant

private const val KEY = "forecasts"
private const val VERSION = 1

// Plain classes, not data classes: no generated toString to print coordinates. Entries stay raw JSON in the
// state so one that won't decode is dropped alone.
@Serializable
private class StoredState(val version: Int, val entries: List<JsonElement>)

@Serializable
private class StoredEntry(
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: String,
    val zone: String,
    val hours: List<StoredHour>,
    val days: List<StoredDay>,
)

@Suppress("LongParameterList") // One field for each of the engine's hourly values, as the spec lists them.
@Serializable
private class StoredHour(
    val start: String,
    val temperatureC: Double,
    val relativeHumidityPercent: Double,
    val windSpeedKmh: Double,
    val visibilityM: Double?,
    val precipitationProbabilityPercent: Double?,
    val precipitationMm: Double,
    val windGustKmh: Double,
    val shortwaveRadiationWm2: Double,
)

@Serializable
private class StoredDay(val date: String, val sunrise: String, val sunset: String)

/** A forecast and when it was fetched. Leaves nothing out of [toString] that it holds. */
class CachedForecast(val fetchedAt: Instant, val forecast: Forecast) {
    override fun toString() = "CachedForecast(hidden)"
}

/**
 * The last forecast fetched for each saved place, kept in [settings] as one JSON string so a write is all or
 * nothing. Keyed by rounded coordinates, so it holds no location the place store doesn't. Read once here;
 * changes are written at once. See docs/bolts/05-main-screen.md, "What's kept".
 */
class ForecastCache(private val settings: Settings) {
    private var entries: Map<Coordinates, CachedForecast> = load()

    fun get(coordinates: Coordinates): CachedForecast? = entries[coordinates.rounded()]

    /** Replaces the entry for these coordinates, so there is one per set of coordinates. */
    fun put(coordinates: Coordinates, fetchedAt: Instant, forecast: Forecast) {
        entries = entries + (coordinates.rounded() to CachedForecast(fetchedAt, forecast))
        save()
    }

    /** Drops every entry whose coordinates aren't in [saved]. */
    fun retain(saved: Set<Coordinates>) {
        val keep = saved.mapTo(HashSet()) { it.rounded() }
        val kept = entries.filterKeys { it in keep }
        if (kept.size == entries.size) return
        entries = kept
        save()
    }

    private fun save() {
        val state = StoredState(VERSION, entries.map { (key, value) -> Json.encodeToJsonElement(toStored(key, value)) })
        settings.putString(KEY, Json.encodeToString(state))
    }

    // A value that won't decode is no cache, and nothing from it goes in a message: it holds coordinates.
    // SerializationException, and the engine types' require, are IllegalArgumentExceptions.
    private fun load(): Map<Coordinates, CachedForecast> {
        val state = try {
            settings.getStringOrNull(KEY)?.let { Json.decodeFromString<StoredState>(it) }
        } catch (_: IllegalArgumentException) {
            null
        }?.takeIf { it.version == VERSION } ?: return emptyMap()
        val loaded = LinkedHashMap<Coordinates, CachedForecast>()
        for (element in state.entries) {
            try {
                val stored = Json.decodeFromJsonElement<StoredEntry>(element)
                loaded[Coordinates(stored.latitude, stored.longitude).rounded()] = stored.toCached()
            } catch (_: IllegalArgumentException) {
                // This entry alone is dropped.
            }
        }
        return loaded
    }
}

private fun toStored(key: Coordinates, value: CachedForecast) = StoredEntry(
    latitude = key.latitude,
    longitude = key.longitude,
    fetchedAt = value.fetchedAt.toString(),
    zone = value.forecast.timeZone.id,
    hours = value.forecast.hours.map {
        StoredHour(
            it.start.toString(),
            it.temperatureC,
            it.relativeHumidityPercent,
            it.windSpeedKmh,
            it.visibilityM,
            it.precipitationProbabilityPercent,
            it.precipitationMm,
            it.windGustKmh,
            it.shortwaveRadiationWm2,
        )
    },
    days = value.forecast.days.map { StoredDay(it.date.toString(), it.sunrise.toString(), it.sunset.toString()) },
)

private fun StoredEntry.toCached() = CachedForecast(
    Instant.parse(fetchedAt),
    Forecast(
        TimeZone.of(zone),
        hours.map {
            HourlyWeather(
                Instant.parse(it.start),
                it.temperatureC,
                it.relativeHumidityPercent,
                it.windSpeedKmh,
                it.visibilityM,
                it.precipitationProbabilityPercent,
                it.precipitationMm,
                it.windGustKmh,
                it.shortwaveRadiationWm2,
            )
        },
        days.map { DailyLight(LocalDate.parse(it.date), Instant.parse(it.sunrise), Instant.parse(it.sunset)) },
    ),
)
