package io.github.dryingday.weather.openmeteo

import io.github.dryingday.engine.DailyLight
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.HourlyWeather
import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86400.0
private const val MAX_HUMIDITY_PERCENT = 100.0

internal val openMeteoJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ForecastResponse(
    val timezone: String,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Long,
    val hourly: HourlyColumns,
    val daily: DailyColumns,
)

@Serializable
private data class HourlyColumns(
    val time: List<Long?>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("relative_humidity_2m") val humidity: List<Double?>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>,
    val visibility: List<Double?>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Double?>,
    val precipitation: List<Double?>,
    @SerialName("wind_gusts_10m") val windGusts: List<Double?>,
    @SerialName("shortwave_radiation") val shortwaveRadiation: List<Double?>,
) {
    val columns get() =
        listOf(
            temperature,
            humidity,
            windSpeed,
            visibility,
            precipitationProbability,
            precipitation,
            windGusts,
            shortwaveRadiation,
        )
}

@Serializable
private data class DailyColumns(
    val time: List<Long?>,
    val sunrise: List<Long?>,
    val sunset: List<Long?>,
    @SerialName("daylight_duration") val daylightDuration: List<Double?>,
)

/**
 * Reads an Open-Meteo forecast response, the way scripts/fetch-reference-forecasts.py does: see "Forecast
 * mapping" in docs/bolts/03-weather-source.md. An hour or a day it can't trust is left out, so the engine gives
 * a null day rather than a guess. Null means the response as a whole can't be read.
 */
internal fun parseForecast(body: String): Forecast? = try {
    openMeteoJson.decodeFromString<ForecastResponse>(body).toForecast()
} catch (_: IllegalArgumentException) {
    // Covers SerializationException, an unknown time zone, and the checks in Forecast.
    null
}

private fun ForecastResponse.toForecast(): Forecast? {
    val isWellFormed =
        hourly.columns.all { it.size == hourly.time.size } &&
            hourly.time.filterNotNull().zipWithNext().all { (a, b) -> a < b } &&
            daily.sunrise.size == daily.time.size &&
            daily.sunset.size == daily.time.size &&
            daily.daylightDuration.size == daily.time.size
    if (!isWellFormed) return null
    val zone = TimeZone.of(timezone)
    return Forecast(zone, (0 until hourly.time.lastIndex).mapNotNull(hourly::hour), days(zone))
}

/** The hour from stamp [i]: preceding-hour values are stamped at its end, instantaneous ones at both ends. */
private fun HourlyColumns.hour(i: Int): HourlyWeather? = try {
    val end = i + 1
    val start = requireNotNull(time[i])
    require(requireNotNull(time[end]) - start == SECONDS_PER_HOUR)
    val first = reading(start, stamp = i, end = end)
    val last = reading(start, stamp = end, end = end)
    val firstVisibility = first.visibilityM
    val lastVisibility = last.visibilityM
    first.copy(
        temperatureC = (first.temperatureC + last.temperatureC) / 2,
        relativeHumidityPercent = (first.relativeHumidityPercent + last.relativeHumidityPercent) / 2,
        windSpeedKmh = (first.windSpeedKmh + last.windSpeedKmh) / 2,
        visibilityM = if (firstVisibility == null || lastVisibility == null) {
            null
        } else {
            (firstVisibility + lastVisibility) / 2
        },
    )
} catch (_: IllegalArgumentException) {
    null
}

/** One stamp's readings. They go through [HourlyWeather]'s checks singly, so averaging can't hide a bad one. */
private fun HourlyColumns.reading(start: Long, stamp: Int, end: Int) = HourlyWeather(
    start = Instant.fromEpochSeconds(start),
    temperatureC = requireNotNull(temperature[stamp]),
    relativeHumidityPercent = requireNotNull(humidity[stamp]).coerceAtMost(MAX_HUMIDITY_PERCENT),
    windSpeedKmh = requireNotNull(windSpeed[stamp]),
    visibilityM = visibility[stamp],
    precipitationProbabilityPercent = precipitationProbability[end],
    precipitationMm = requireNotNull(precipitation[end]),
    windGustKmh = requireNotNull(windGusts[end]),
    shortwaveRadiationWm2 = requireNotNull(shortwaveRadiation[end]),
)

private fun ForecastResponse.days(zone: TimeZone): List<DailyLight> = daily.time.indices
    .mapNotNull { day(it, zone) }
    .groupBy { it.date }
    .values
    .mapNotNull { it.singleOrNull() }
    .sortedBy { it.date }

private fun ForecastResponse.day(i: Int, zone: TimeZone): DailyLight? = try {
    val dayStart = requireNotNull(daily.time[i])
    val sunrise = Instant.fromEpochSeconds(requireNotNull(daily.sunrise[i]))
    val sunset = Instant.fromEpochSeconds(requireNotNull(daily.sunset[i]))
    val daylight = requireNotNull(daily.daylightDuration[i])
    require(sunrise <= sunset)
    if (daylight == 0.0 || daylight >= SECONDS_PER_DAY) {
        // Open-Meteo's "midnight" is at its one fixed offset, so rebuild it in the real zone.
        val date = Instant.fromEpochSeconds(dayStart + utcOffsetSeconds).toLocalDateTime(TimeZone.UTC).date
        val midnight = date.atStartOfDayIn(zone)
        val end = if (daylight == 0.0) midnight else date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        DailyLight(date, midnight, end)
    } else {
        DailyLight(sunrise.toLocalDateTime(zone).date, sunrise, sunset)
    }
} catch (_: IllegalArgumentException) {
    null
} catch (_: DateTimeArithmeticException) {
    // A time too far out to be a date.
    null
}
