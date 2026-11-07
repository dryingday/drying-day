package io.github.dryingday.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Rule 1 of docs/bolts/02-drying-rules.md, and daylight clipped to the local date. */
class UsableTimeTest {
    private val london = TimeZone.of("Europe/London")
    private val oslo = TimeZone.of("Europe/Oslo")

    private fun at(iso: String) = Instant.parse(iso)

    private fun hourFrom(start: Instant) = HourlyWeather(
        start = start,
        temperatureC = 20.0,
        relativeHumidityPercent = 50.0,
        windSpeedKmh = 10.0,
        visibilityM = null,
        precipitationProbabilityPercent = null,
        precipitationMm = 0.0,
        windGustKmh = 20.0,
        shortwaveRadiationWm2 = 400.0,
    )

    // 14 June 2025 at Greenwich: sunrise 04:42 BST, sunset 21:21 BST.
    private val june = DailyLight(LocalDate(2025, 6, 14), at("2025-06-14T03:42:00Z"), at("2025-06-14T20:21:00Z"))
    private val juneDaylight = june.daylight(london)
    private val earlyMorning = at("2025-06-14T00:00:00Z")

    @Test
    fun aWholeHourInDaylightCountsInFull() {
        assertEquals(1.hours, hourFrom(at("2025-06-14T11:00:00Z")).usableTime(juneDaylight, earlyMorning))
    }

    @Test
    fun theSunriseHourCountsFromSunrise() {
        assertEquals(18.minutes, hourFrom(at("2025-06-14T03:00:00Z")).usableTime(juneDaylight, earlyMorning))
    }

    @Test
    fun theSunsetHourCountsUntilSunset() {
        assertEquals(21.minutes, hourFrom(at("2025-06-14T20:00:00Z")).usableTime(juneDaylight, earlyMorning))
    }

    @Test
    fun hoursInTheDarkCountForNothing() {
        assertEquals(Duration.ZERO, hourFrom(at("2025-06-14T01:00:00Z")).usableTime(juneDaylight, earlyMorning))
        assertEquals(Duration.ZERO, hourFrom(at("2025-06-14T21:00:00Z")).usableTime(juneDaylight, earlyMorning))
    }

    @Test
    fun todayCountsOnlyAfterNow() {
        val now = at("2025-06-14T10:40:00Z")
        assertEquals(20.minutes, hourFrom(at("2025-06-14T10:00:00Z")).usableTime(juneDaylight, now))
        assertEquals(Duration.ZERO, hourFrom(at("2025-06-14T09:00:00Z")).usableTime(juneDaylight, now))
        assertEquals(1.hours, hourFrom(at("2025-06-14T11:00:00Z")).usableTime(juneDaylight, now))
    }

    @Test
    fun daylightIsSunriseToSunsetWithinTheDate() {
        assertEquals(Interval(june.sunrise, june.sunset), juneDaylight)
    }

    @Test
    fun polarDayRunsMidnightToMidnight() {
        // As Open-Meteo gives it at 69.65 N on 18 May 2025.
        val day = DailyLight(LocalDate(2025, 5, 18), at("2025-05-17T22:00:00Z"), at("2025-05-18T22:00:00Z"))
        assertEquals(24.hours, day.daylight(oslo).length)
    }

    @Test
    fun aSunsetAfterMidnightIsClippedToTheDate() {
        // The first day of the midnight sun: the sun sets at 00:17 on the next date.
        val day = DailyLight(LocalDate(2025, 5, 17), at("2025-05-16T23:14:22Z"), at("2025-05-17T22:17:37Z"))
        assertEquals(Interval(day.sunrise, at("2025-05-17T22:00:00Z")), day.daylight(oslo))
    }

    @Test
    fun polarNightHasNoDaylight() {
        val midnight = at("2025-11-27T23:00:00Z")
        val day = DailyLight(LocalDate(2025, 11, 28), midnight, midnight)
        assertEquals(Duration.ZERO, day.daylight(oslo).length)
        assertEquals(Duration.ZERO, hourFrom(midnight).usableTime(day.daylight(oslo), midnight))
    }

    @Test
    fun aClockChangeDayIsClippedToItsRealLength() {
        // 30 March 2025 in London is 23 hours long; 26 October is 25.
        val spring = DailyLight(LocalDate(2025, 3, 30), at("2025-03-29T00:00:00Z"), at("2025-03-31T00:00:00Z"))
        val autumn = DailyLight(LocalDate(2025, 10, 26), at("2025-10-25T00:00:00Z"), at("2025-10-27T00:00:00Z"))
        assertEquals(23.hours, spring.daylight(london).length)
        assertEquals(25.hours, autumn.daylight(london).length)
    }
}
