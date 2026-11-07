package io.github.dryingday.reference

import io.github.dryingday.engine.DailyLight
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.HourlyWeather
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * A real past forecast at a public landmark, for checking the drying rules against
 * (docs/bolts/02-drying-rules.md, "Reference set"). The engine is asked about [date] at [now].
 */
internal class ReferenceCase(
    val id: String,
    val landmark: String,
    val date: LocalDate,
    val now: Instant,
    val forecast: Forecast,
) {
    override fun toString() = id
}

/**
 * Builds a [Forecast] from the generated columns: one entry per hour, starting at [starts] (epoch seconds).
 * In the nullable columns, [visibilityM] and [precipitationProbabilityPercent], NaN stands for null.
 */
@Suppress("LongParameterList")
internal fun referenceForecast(
    zone: String,
    starts: LongArray,
    temperatureC: DoubleArray,
    relativeHumidityPercent: DoubleArray,
    windSpeedKmh: DoubleArray,
    visibilityM: DoubleArray,
    precipitationProbabilityPercent: DoubleArray,
    precipitationMm: DoubleArray,
    windGustKmh: DoubleArray,
    shortwaveRadiationWm2: DoubleArray,
    days: List<DailyLight>,
): Forecast {
    val columns = listOf(
        temperatureC,
        relativeHumidityPercent,
        windSpeedKmh,
        visibilityM,
        precipitationProbabilityPercent,
        precipitationMm,
        windGustKmh,
        shortwaveRadiationWm2,
    )
    require(columns.all { it.size == starts.size }) { "every column needs one value per hour" }
    val hours = starts.indices.map { i ->
        HourlyWeather(
            start = Instant.fromEpochSeconds(starts[i]),
            temperatureC = temperatureC[i],
            relativeHumidityPercent = relativeHumidityPercent[i],
            windSpeedKmh = windSpeedKmh[i],
            visibilityM = visibilityM[i].orNull(),
            precipitationProbabilityPercent = precipitationProbabilityPercent[i].orNull(),
            precipitationMm = precipitationMm[i],
            windGustKmh = windGustKmh[i],
            shortwaveRadiationWm2 = shortwaveRadiationWm2[i],
        )
    }
    return Forecast(TimeZone.of(zone), hours, days)
}

internal fun referenceDay(year: Int, month: Int, day: Int, sunrise: Long, sunset: Long) =
    DailyLight(LocalDate(year, month, day), Instant.fromEpochSeconds(sunrise), Instant.fromEpochSeconds(sunset))

private fun Double.orNull(): Double? = if (isNaN()) null else this
