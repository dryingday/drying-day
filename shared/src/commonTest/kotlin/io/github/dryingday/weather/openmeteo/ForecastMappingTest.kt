package io.github.dryingday.weather.openmeteo

import io.github.dryingday.engine.Forecast
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

private const val HOUR = 3600L
private const val DAY = 86400L
private const val DAYLIGHT = 43200.0
private const val HUMIDITY = "relative_humidity_2m"
private const val PRECIPITATION = "precipitation"
private const val PROBABILITY = "precipitation_probability"
private const val VISIBILITY = "visibility"

private val start = Instant.parse("2025-06-14T13:00:00Z").epochSeconds
private val midnight = Instant.parse("2025-06-14T00:00:00Z").epochSeconds

private val instantaneous = listOf("temperature_2m", HUMIDITY, "wind_speed_10m")
private val preceding = listOf(PRECIPITATION, "wind_gusts_10m", "shortwave_radiation")

private fun epoch(text: String) = Instant.parse(text).epochSeconds

private fun hourly(count: Int = 2, vararg overrides: Pair<String, List<Number?>>): Map<String, List<Number?>> = mapOf(
    "time" to List(count) { start + it * HOUR },
    "temperature_2m" to List(count) { 20.0 },
    HUMIDITY to List(count) { 50.0 },
    "wind_speed_10m" to List(count) { 10.0 },
    VISIBILITY to List(count) { 20000.0 },
    PROBABILITY to List(count) { 10 },
    PRECIPITATION to List(count) { 0.0 },
    "wind_gusts_10m" to List(count) { 20.0 },
    "shortwave_radiation" to List(count) { 400.0 },
) + overrides

private fun daily(vararg overrides: Pair<String, List<Number?>>): Map<String, List<Number?>> = mapOf(
    "time" to listOf(midnight),
    "sunrise" to listOf(midnight + 6 * HOUR),
    "sunset" to listOf(midnight + 18 * HOUR),
    "daylight_duration" to listOf(DAYLIGHT),
) + overrides

private fun column(values: List<Number?>) = values.joinToString(",", "[", "]") { it?.toString() ?: "null" }

private fun object_(columns: Map<String, List<Number?>>) =
    columns.entries.joinToString(",", "{", "}") { (name, values) -> "\"$name\":${column(values)}" }

private fun body(
    zone: String = "Etc/UTC",
    offset: Long = 0,
    hours: Map<String, List<Number?>> = hourly(),
    days: Map<String, List<Number?>> = daily(),
) = """{"timezone":"$zone","utc_offset_seconds":$offset,"hourly":${object_(hours)},"daily":${object_(days)}}"""

private fun parsed(text: String): Forecast = assertNotNull(parseForecast(text))

class ForecastMappingTest {
    @Test
    fun aShowerStampedAtTwoLandsInTheHourFromOne() {
        val hours =
            hourly(
                2,
                PRECIPITATION to listOf(9.9, 1.5),
                PROBABILITY to listOf(99, 70),
                "wind_gusts_10m" to listOf(99.0, 45.0),
                "shortwave_radiation" to listOf(999.0, 120.0),
            )

        val hour = parsed(body(hours = hours)).hours.single()

        assertEquals(Instant.fromEpochSeconds(start), hour.start)
        assertEquals(1.5, hour.precipitationMm)
        assertEquals(70.0, hour.precipitationProbabilityPercent)
        assertEquals(45.0, hour.windGustKmh)
        assertEquals(120.0, hour.shortwaveRadiationWm2)
    }

    @Test
    fun instantaneousValuesAreTheMeanOfBothEnds() {
        val hours =
            hourly(
                2,
                "temperature_2m" to listOf(10.0, 13.0),
                HUMIDITY to listOf(60, 71),
                "wind_speed_10m" to listOf(4.0, 8.0),
                VISIBILITY to listOf(1000.0, 3000.0),
            )

        val hour = parsed(body(hours = hours)).hours.single()

        assertEquals(11.5, hour.temperatureC)
        assertEquals(65.5, hour.relativeHumidityPercent)
        assertEquals(6.0, hour.windSpeedKmh)
        assertEquals(2000.0, hour.visibilityM)
    }

    @Test
    fun humidityIsClampedBeforeAveraging() {
        val hours = hourly(2, HUMIDITY to listOf(102, 90))

        assertEquals(95.0, parsed(body(hours = hours)).hours.single().relativeHumidityPercent)
    }

    @Test
    fun nStampsGiveOneHourFewer() {
        assertEquals(3, parsed(body(hours = hourly(4))).hours.size)
    }

    @Test
    fun anHourMissingAnInstantaneousValueAtEitherEndIsDropped() {
        for (field in instantaneous) {
            for (nullAt in 0..1) {
                val values = List<Number?>(2) { if (it == nullAt) null else 10.0 }

                assertEquals(0, parsed(body(hours = hourly(2, field to values))).hours.size, "$field at $nullAt")
            }
        }
    }

    @Test
    fun anHourMissingAPrecedingValueIsDroppedOnlyWhenItsEndStampIs() {
        for (field in preceding) {
            val atEnd = body(hours = hourly(2, field to listOf(1.0, null)))
            val atStart = body(hours = hourly(2, field to listOf(null, 1.0)))

            assertEquals(0, parsed(atEnd).hours.size, field)
            assertEquals(1, parsed(atStart).hours.size, field)
        }
    }

    @Test
    fun anHourWithAValueTheEngineRejectsIsDroppedAndTheRestKept() {
        val hours = hourly(3, PRECIPITATION to listOf(0.0, -1.0, 0.0))

        assertEquals(listOf(Instant.fromEpochSeconds(start + HOUR)), parsed(body(hours = hours)).hours.map { it.start })
        assertEquals(0, parsed(body(hours = hourly(2, "temperature_2m" to listOf(999.0, 999.0)))).hours.size)
    }

    @Test
    fun oneBadReadingCannotBeAveragedIntoAGoodOne() {
        val cases =
            listOf(
                "temperature_2m" to listOf(100.0, 10.0),
                HUMIDITY to listOf(-20.0, 60.0),
                "wind_speed_10m" to listOf(-4.0, 8.0),
                VISIBILITY to listOf(-100.0, 3000.0),
            )

        for ((field, values) in cases) {
            assertEquals(0, parsed(body(hours = hourly(2, field to values))).hours.size, field)
        }
    }

    @Test
    fun aNullStampDropsOnlyTheHoursThatNeedIt() {
        val hours = hourly(4, "time" to listOf(start, null, start + 2 * HOUR, start + 3 * HOUR))

        assertEquals(
            listOf(Instant.fromEpochSeconds(start + 2 * HOUR)),
            parsed(body(hours = hours)).hours.map {
                it.start
            },
        )
    }

    @Test
    fun visibilityIsNullIfEitherEndIs() {
        for (values in listOf(listOf(null, 1000.0), listOf(1000.0, null))) {
            val hour = parsed(body(hours = hourly(2, VISIBILITY to values))).hours.single()

            assertNull(hour.visibilityM)
        }
    }

    @Test
    fun aNullProbabilityStaysNull() {
        val hour = parsed(body(hours = hourly(2, PROBABILITY to listOf(10, null)))).hours.single()

        assertNull(hour.precipitationProbabilityPercent)
    }

    @Test
    fun stampsThatAreNotAnHourApartGiveNoHour() {
        val hours = hourly(3, "time" to listOf(start, start + HOUR, start + 3 * HOUR))

        assertEquals(listOf(Instant.fromEpochSeconds(start)), parsed(body(hours = hours)).hours.map { it.start })
    }

    @Test
    fun theZoneIsTheResponses() {
        assertEquals(TimeZone.of("Pacific/Auckland"), parsed(body(zone = "Pacific/Auckland")).timeZone)
    }

    @Test
    fun anOrdinaryDayIsDatedBySunriseInTheZone() {
        val sunrise = epoch("2025-01-10T18:00:00Z")
        val days =
            daily(
                "time" to listOf(epoch("2025-01-10T11:00:00Z")),
                "sunrise" to listOf(sunrise),
                "sunset" to listOf(sunrise + 14 * HOUR),
            )

        val day = parsed(body(zone = "Pacific/Auckland", offset = 46800, days = days)).days.single()

        assertEquals(LocalDate(2025, 1, 11), day.date)
        assertEquals(Instant.fromEpochSeconds(sunrise), day.sunrise)
        assertEquals(Instant.fromEpochSeconds(sunrise + 14 * HOUR), day.sunset)
    }

    @Test
    fun polarDayIsRebuiltFromRealLocalMidnightAcrossAClockChange() {
        // Oslo's clocks go forward on 30 March 2025, so that local day is 23 hours long. The response's
        // one fixed offset is summer time, so its "midnight" is an hour away from the real one.
        val fixedMidnight = epoch("2025-03-29T22:00:00Z")
        val days =
            daily(
                "time" to listOf(fixedMidnight),
                "sunrise" to listOf(fixedMidnight),
                "sunset" to listOf(fixedMidnight + DAY),
                "daylight_duration" to listOf(DAY.toDouble()),
            )

        val day = parsed(body(zone = "Europe/Oslo", offset = 7200, days = days)).days.single()

        assertEquals(LocalDate(2025, 3, 30), day.date)
        assertEquals(Instant.parse("2025-03-29T23:00:00Z"), day.sunrise)
        assertEquals(Instant.parse("2025-03-30T22:00:00Z"), day.sunset)
    }

    @Test
    fun polarNightIsRebuiltFromRealLocalMidnightAcrossAClockChange() {
        val fixedMidnight = epoch("2025-03-29T22:00:00Z")
        val days =
            daily(
                "time" to listOf(fixedMidnight),
                "sunrise" to listOf(fixedMidnight),
                "sunset" to listOf(fixedMidnight),
                "daylight_duration" to listOf(0.0),
            )

        val day = parsed(body(zone = "Europe/Oslo", offset = 7200, days = days)).days.single()

        assertEquals(LocalDate(2025, 3, 30), day.date)
        assertEquals(Instant.parse("2025-03-29T23:00:00Z"), day.sunrise)
        assertEquals(day.sunrise, day.sunset)
    }

    @Test
    fun aDayWithAnyNullFieldIsDropped() {
        for (field in listOf("time", "sunrise", "sunset", "daylight_duration")) {
            assertEquals(0, parsed(body(days = daily(field to listOf(null)))).days.size, field)
        }
    }

    @Test
    fun aDayWhoseSunsetIsBeforeItsSunriseIsDropped() {
        val days = daily("sunset" to listOf(midnight))

        assertEquals(0, parsed(body(days = days)).days.size)
    }

    @Test
    fun aPolarDayWhoseSunsetIsBeforeItsSunriseIsDropped() {
        for (daylight in listOf(0.0, DAY.toDouble())) {
            val days = daily("sunset" to listOf(midnight), "daylight_duration" to listOf(daylight))

            assertEquals(0, parsed(body(days = days)).days.size, "$daylight")
        }
    }

    @Test
    fun aSunTimeTooFarOutToBeADateIsDropped() {
        val days = daily("sunrise" to listOf(Long.MAX_VALUE), "sunset" to listOf(Long.MAX_VALUE))

        assertEquals(0, parsed(body(days = days)).days.size)
    }

    @Test
    fun twoDaysOnOneDateAreBothDroppedAndOthersKept() {
        val days =
            daily(
                "time" to listOf(midnight, midnight + DAY, midnight + 2 * DAY),
                "sunrise" to listOf(midnight + 6 * HOUR, midnight + 7 * HOUR, midnight + 2 * DAY + 6 * HOUR),
                "sunset" to listOf(midnight + 18 * HOUR, midnight + 19 * HOUR, midnight + 2 * DAY + 18 * HOUR),
                "daylight_duration" to listOf(DAYLIGHT, DAYLIGHT, DAYLIGHT),
            )

        assertEquals(listOf(LocalDate(2025, 6, 16)), parsed(body(days = days)).days.map { it.date })
    }

    @Test
    fun anUnknownZoneIsBad() {
        assertNull(parseForecast(body(zone = "Nowhere/Testville")))
    }

    @Test
    fun columnsOfDifferentLengthsAreBad() {
        assertNull(parseForecast(body(hours = hourly(3, PRECIPITATION to listOf(0.0, 0.0)))))
        assertNull(parseForecast(body(days = daily("sunset" to emptyList()))))
    }

    @Test
    fun stampsOutOfOrderAreBad() {
        assertNull(parseForecast(body(hours = hourly(3, "time" to listOf(start, start + 2 * HOUR, start + HOUR)))))
    }

    @Test
    fun aMissingColumnIsBad() {
        assertNull(parseForecast(body(hours = hourly() - PRECIPITATION)))
        assertNull(parseForecast(body(days = daily() - "daylight_duration")))
    }

    @Test
    fun aBodyThatIsNotTheExpectedJsonIsBad() {
        assertNull(parseForecast("not json"))
        assertNull(parseForecast("""{"error":true,"reason":"nope"}"""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = body().replaceFirst("{", """{"elevation":17.0,""")

        assertEquals(1, parsed(text).hours.size)
    }
}
