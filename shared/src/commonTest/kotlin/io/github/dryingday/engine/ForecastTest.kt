package io.github.dryingday.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ForecastTest {
    private val noon = Instant.parse("2025-06-14T12:00:00Z")

    private fun hour(
        start: Instant = noon,
        relativeHumidityPercent: Double = 60.0,
        precipitationProbabilityPercent: Double? = 10.0,
        precipitationMm: Double = 0.0,
        windSpeedKmh: Double = 12.0,
        visibilityM: Double? = 20_000.0,
        temperatureC: Double = 18.0,
    ) = HourlyWeather(
        start = start,
        temperatureC = temperatureC,
        relativeHumidityPercent = relativeHumidityPercent,
        windSpeedKmh = windSpeedKmh,
        visibilityM = visibilityM,
        precipitationProbabilityPercent = precipitationProbabilityPercent,
        precipitationMm = precipitationMm,
        windGustKmh = 25.0,
        shortwaveRadiationWm2 = 500.0,
    )

    private val day = DailyLight(
        date = LocalDate(2025, 6, 14),
        sunrise = Instant.parse("2025-06-14T03:42:00Z"),
        sunset = Instant.parse("2025-06-14T20:21:00Z"),
    )

    @Test
    fun acceptsTheNullableFieldsAsNull() {
        val forecast = Forecast(
            timeZone = TimeZone.of("Europe/London"),
            hours = listOf(hour(precipitationProbabilityPercent = null, visibilityM = null)),
            days = listOf(day),
        )
        assertEquals(null, forecast.hours.single().visibilityM)
    }

    @Test
    fun acceptsPolarNightWithSunriseEqualToSunset() {
        val midnight = Instant.parse("2025-11-27T23:00:00Z")
        DailyLight(LocalDate(2025, 11, 28), sunrise = midnight, sunset = midnight)
    }

    @Test
    fun rejectsPercentagesOutsideZeroToHundred() {
        assertFailsWith<IllegalArgumentException> { hour(relativeHumidityPercent = 100.5) }
        assertFailsWith<IllegalArgumentException> { hour(relativeHumidityPercent = -1.0) }
        assertFailsWith<IllegalArgumentException> { hour(precipitationProbabilityPercent = 101.0) }
    }

    @Test
    fun rejectsNegativeAmounts() {
        assertFailsWith<IllegalArgumentException> { hour(precipitationMm = -0.1) }
        assertFailsWith<IllegalArgumentException> { hour(windSpeedKmh = -1.0) }
        assertFailsWith<IllegalArgumentException> { hour(visibilityM = -1.0) }
    }

    @Test
    fun rejectsNotANumber() {
        assertFailsWith<IllegalArgumentException> { hour(relativeHumidityPercent = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { hour(precipitationMm = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { hour(temperatureC = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { hour(temperatureC = Double.NEGATIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { hour(windSpeedKmh = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { hour(visibilityM = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun rejectsSunsetBeforeSunrise() {
        assertFailsWith<IllegalArgumentException> {
            DailyLight(day.date, sunrise = day.sunset, sunset = day.sunrise)
        }
    }

    @Test
    fun rejectsHoursOutOfOrderOrRepeated() {
        val zone = TimeZone.of("Europe/London")
        assertFailsWith<IllegalArgumentException> {
            Forecast(zone, listOf(hour(start = noon + 1.hours), hour(start = noon)), listOf(day))
        }
        assertFailsWith<IllegalArgumentException> {
            Forecast(zone, listOf(hour(start = noon), hour(start = noon)), listOf(day))
        }
    }

    @Test
    fun rejectsOverlappingHours() {
        val zone = TimeZone.of("Europe/London")
        assertFailsWith<IllegalArgumentException> {
            Forecast(zone, listOf(hour(start = noon), hour(start = noon + 30.minutes)), listOf(day))
        }
    }

    @Test
    fun acceptsGapsAndHalfHourOffsets() {
        val kolkataNoon = Instant.parse("2025-06-14T06:30:00Z")
        Forecast(
            TimeZone.of("Asia/Kolkata"),
            listOf(hour(start = kolkataNoon), hour(start = kolkataNoon + 3.hours)),
            emptyList(),
        )
    }

    @Test
    fun rejectsDaysOutOfOrderOrRepeated() {
        val zone = TimeZone.of("Europe/London")
        assertFailsWith<IllegalArgumentException> { Forecast(zone, emptyList(), listOf(day, day)) }
    }
}
