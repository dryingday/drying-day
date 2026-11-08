package io.github.dryingday.reference

import io.github.dryingday.engine.DailyLight
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.HourlyWeather
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Verdict
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

/** The reference set, built once for every test that uses it. */
internal val allReferenceCases: List<ReferenceCase> by lazy { referenceCases() }

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

/** How the maintainer settled a case and load where the engine and the blind label disagreed. */
internal enum class Decision {
    /** No disagreement: the label stands. */
    AGREED,

    /** The label was wrong; the expectation is the maintainer's answer. */
    LABEL_WRONG,

    /** Left open: a known disagreement, not asserted. See docs/reference-set.md. */
    OPEN,
}

/** What ReferenceSetTest expects of one case and load. Times are local "HH:MM", null for DONT_BOTHER. */
internal class ReferenceExpectation(
    val id: String,
    val load: Load,
    val verdict: Verdict,
    val hangOut: String?,
    val bringIn: String?,
    val decision: Decision,
)
