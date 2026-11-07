package io.github.dryingday.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * What the drying engine reads: an hourly forecast and the daylight of each day, for one place.
 * Independent of any weather source; see docs/bolts/02-drying-rules.md, "Input".
 *
 * @property timeZone the IANA zone of the forecast's location, never the device's. Days are cut in it.
 * @property hours in increasing order of [HourlyWeather.start], at least an hour apart so none overlap.
 *   A gap is a missing hour.
 * @property days in strictly increasing order of [DailyLight.date].
 */
data class Forecast(val timeZone: TimeZone, val hours: List<HourlyWeather>, val days: List<DailyLight>) {
    init {
        require(hours.zipWithNext().all { (a, b) -> b.start - a.start >= 1.hours }) {
            "hours must be in order, at least an hour apart"
        }
        require(days.zipWithNext().all { (a, b) -> a.date < b.date }) { "days must be in order, each once" }
    }
}

/**
 * The weather for the hour from [start] to [start] + 1 h.
 *
 * Open-Meteo stamps instantaneous values (temperature, humidity, wind speed, visibility) at a moment, and
 * precipitation, its probability, gusts and radiation at the end of the hour they cover. Mapping a response
 * onto this type is the source's job; the spec says how.
 *
 * @property visibilityM null when the source doesn't give it; counts as clear.
 * @property precipitationProbabilityPercent null when the source doesn't give it; then [precipitationMm] alone
 *   decides whether the hour is wet.
 */
data class HourlyWeather(
    val start: Instant,
    val temperatureC: Double,
    val relativeHumidityPercent: Double,
    val windSpeedKmh: Double,
    val visibilityM: Double?,
    val precipitationProbabilityPercent: Double?,
    val precipitationMm: Double,
    val windGustKmh: Double,
    val shortwaveRadiationWm2: Double,
) {
    init {
        require(temperatureC.isFinite()) { "temperature must be a number" }
        requirePercent(relativeHumidityPercent, "relative humidity")
        precipitationProbabilityPercent?.let { requirePercent(it, "precipitation probability") }
        requireNonNegative(windSpeedKmh, "wind speed")
        visibilityM?.let { requireNonNegative(it, "visibility") }
        requireNonNegative(precipitationMm, "precipitation")
        requireNonNegative(windGustKmh, "wind gust")
        requireNonNegative(shortwaveRadiationWm2, "shortwave radiation")
    }
}

/**
 * Sunrise and sunset for one local [date]. Daylight is the time between them, clipped to that date in the
 * forecast's zone: polar day is midnight to midnight, polar night has [sunrise] equal to [sunset].
 */
data class DailyLight(val date: LocalDate, val sunrise: Instant, val sunset: Instant) {
    init {
        require(sunrise <= sunset) { "sunset must not be before sunrise" }
    }
}

private const val MAX_PERCENT = 100.0

private fun requirePercent(value: Double, name: String) =
    require(value in 0.0..MAX_PERCENT) { "$name must be 0 to 100, was $value" }

private fun requireNonNegative(value: Double, name: String) =
    require(value >= 0.0 && value.isFinite()) { "$name must be zero or more, was $value" }
