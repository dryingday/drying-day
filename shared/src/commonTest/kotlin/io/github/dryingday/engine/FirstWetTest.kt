package io.github.dryingday.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Instant

private val dawn = at(day0, 5 to 0)

/** Rain on [date] during the local [hours], else the sunny default. */
private fun rainAt(date: LocalDate, hours: IntRange): (LocalDate, Int) -> W = { d, h ->
    if (d == date && h in hours) rain else sunny
}

/**
 * The spec's definition, read off the classification: the first hour the engine's `kind()` calls WET that has
 * usable time, from the start of that usable time. Null when there is none.
 */
private fun Forecast.expectedFirstWet(date: LocalDate, now: Instant): Instant? {
    val daylight = days.first { it.date == date }.daylight(timeZone)
    val wet = hours.firstOrNull { it.kind() == HourKind.WET && it.usableTime(daylight, now) > Duration.ZERO }
    return wet?.let { maxOf(it.start, daylight.start, now) }
}

class FirstWetTest {
    @Test
    fun aWetFirstHourGivesItsStart() {
        val forecast = forecast(weather = rainAt(day0, 6..7))

        val wet = firstWet(forecast, day0, dawn)

        assertEquals(at(day0, 6 to 0), wet)
        assertEquals(forecast.expectedFirstWet(day0, dawn), wet)
    }

    @Test
    fun theFirstWetHourWinsWhereverItFalls() {
        val forecast = forecast(weather = rainAt(day0, 15..17))

        assertEquals(at(day0, 15 to 0), firstWet(forecast, day0, dawn))
    }

    @Test
    fun aHighRainChanceIsWetToo() {
        val forecast = forecast { _, h -> if (h == 12) W(rainChancePercent = 70.0) else sunny }
        val wet = assertNotNull(firstWet(forecast, day0, dawn))

        assertEquals(HourKind.WET, forecast.hours.first { it.start == wet }.kind())
        assertEquals(at(day0, 12 to 0), wet)
    }

    @Test
    fun theHourBeforeSunriseStartsAtSunrise() {
        val forecast = forecast(sunrise = 5 to 30, weather = rainAt(day0, 5..5))

        assertEquals(at(day0, 5 to 30), firstWet(forecast, day0, at(day0, 4 to 0)))
    }

    @Test
    fun rainOnlyAfterSunsetOrBeforeSunriseIsNone() {
        val forecast = forecast { _, h -> if (h < 6 || h >= 20) rain else sunny }

        assertNull(firstWet(forecast, day0, at(day0, 0 to 30)))
        assertNull(forecast.expectedFirstWet(day0, at(day0, 0 to 30)))
    }

    @Test
    fun rainInTheHourOfSunsetStopsAtSunset() {
        val forecast = forecast(sunset = 19 to 30, weather = rainAt(day0, 19..19))

        assertEquals(at(day0, 19 to 0), firstWet(forecast, day0, dawn))
        assertNull(firstWet(forecast(sunset = 19 to 0, weather = rainAt(day0, 19..19)), day0, dawn))
    }

    @Test
    fun todayFromNowPartwayThroughAWetHourStartsAtNow() {
        val forecast = forecast(weather = rainAt(day0, 10..10))
        val now = at(day0, 10 to 20)

        val wet = firstWet(forecast, day0, now)

        assertEquals(now, wet)
        assertEquals(forecast.expectedFirstWet(day0, now), wet)
    }

    @Test
    fun aWetHourThatEndedBeforeNowIsNotCounted() {
        val forecast = forecast(weather = rainAt(day0, 9..9))
        val now = at(day0, 10 to 0)

        assertNull(firstWet(forecast, day0, now))
        assertNull(forecast.expectedFirstWet(day0, now))
    }

    @Test
    fun aLaterWetHourStillCountsWhenAnEarlierOneIsPast() {
        val forecast = forecast { _, h -> if (h == 9 || h == 14) rain else sunny }

        assertEquals(at(day0, 14 to 0), firstWet(forecast, day0, at(day0, 10 to 0)))
    }

    @Test
    fun tomorrowIsReadFromItsOwnSunrise() {
        val forecast = forecast(weather = rainAt(day0.plus(1, DateTimeUnit.DAY), 3..7))
        val tomorrow = day0.plus(1, DateTimeUnit.DAY)

        assertEquals(at(tomorrow, 6 to 0), firstWet(forecast, tomorrow, at(day0, 22 to 0)))
        assertNull(firstWet(forecast, day0, dawn))
    }

    @Test
    fun aDateBeforeTodayOrAfterTodayPlusTwoIsNone() {
        val early = forecast(weather = { _, _ -> rain })
        val late = forecast(first = day0.plus(3, DateTimeUnit.DAY), weather = { _, _ -> rain })
        val both = Forecast(london, early.hours + late.hours, early.days + late.days)
        val yesterday = at(day0.plus(1, DateTimeUnit.DAY), 8 to 0)

        assertNull(firstWet(both, day0, yesterday))
        assertNotNull(firstWet(both, day0.plus(1, DateTimeUnit.DAY), yesterday))
        assertNotNull(firstWet(both, day0.plus(2, DateTimeUnit.DAY), yesterday))
        assertNotNull(firstWet(both, day0.plus(3, DateTimeUnit.DAY), yesterday))
        assertNull(firstWet(both, day0.plus(4, DateTimeUnit.DAY), yesterday))
        // From day0 itself, day0 + 3 is out of range.
        assertNotNull(firstWet(both, day0.plus(2, DateTimeUnit.DAY), dawn))
        assertNull(firstWet(both, day0.plus(3, DateTimeUnit.DAY), dawn))
    }

    @Test
    fun aDryDayIsNone() {
        val forecast = forecast()

        assertNull(firstWet(forecast, day0, dawn))
        assertNull(forecast.expectedFirstWet(day0, dawn))
    }

    @Test
    fun polarNightIsNone() {
        val forecast = forecast(oslo, polar = Polar.NIGHT) { _, _ -> rain }
        val now = at(day0, 5 to 0, oslo)

        assertNull(firstWet(forecast, day0, now))
        assertNull(forecast.expectedFirstWet(day0, now))
    }

    @Test
    fun polarDayCountsRainAtAnyHour() {
        val forecast = forecast(oslo, polar = Polar.DAY, weather = rainAt(day0, 2..2))

        assertEquals(at(day0, 2 to 0, oslo), firstWet(forecast, day0, at(day0, 0 to 0, oslo)))
    }

    @Test
    fun aDayTheEngineGivesNoAdviceForIsNone() {
        val missing = at(day0, 10 to 0)
        val gap = forecast(skipHours = setOf(missing), weather = rainAt(day0, 15..16))
        val noDaylight = forecast(skipDays = setOf(day0), weather = { _, _ -> rain })

        assertNull(advise(gap, Load.LIGHT, dawn).first())
        assertNull(firstWet(gap, day0, dawn))
        assertNull(advise(noDaylight, Load.LIGHT, dawn).first())
        assertNull(firstWet(noDaylight, day0, dawn))
    }

    @Test
    fun aGapOutsideTheUsableTimeDoesNotMakeTheDayNull() {
        val gone = at(day0, 3 to 0)
        val forecast = forecast(skipHours = setOf(gone), weather = rainAt(day0, 15..15))

        assertNotNull(advise(forecast, Load.LIGHT, dawn).first())
        assertEquals(at(day0, 15 to 0), firstWet(forecast, day0, dawn))
    }

    @Test
    fun aWetHourWhenTheClocksGoForward() {
        // London's clocks go forward at 01:00 UTC on 30 March 2025, so that local day has 23 hours.
        val change = LocalDate(2025, 3, 30)
        val forecast = forecast(london, LocalDate(2025, 3, 29), weather = rainAt(change, 13..13))
        val now = at(LocalDate(2025, 3, 29), 22 to 0)

        val wet = firstWet(forecast, change, now)

        assertEquals(at(change, 13 to 0), wet)
        assertEquals(Instant.parse("2025-03-30T12:00:00Z"), wet)
        assertEquals(forecast.expectedFirstWet(change, now), wet)
    }

    @Test
    fun aWetHourWhenTheClocksGoBack() {
        // London's clocks go back at 01:00 UTC on 26 October 2025, so that local day has 25 hours.
        val change = LocalDate(2025, 10, 26)
        val forecast = forecast(london, LocalDate(2025, 10, 25), weather = rainAt(change, 8..8))
        val now = at(LocalDate(2025, 10, 25), 22 to 0)

        val wet = firstWet(forecast, change, now)

        assertEquals(Instant.parse("2025-10-26T08:00:00Z"), wet)
        assertEquals(forecast.expectedFirstWet(change, now), wet)
    }

    @Test
    fun aWillDryDayWithRainAfterBringInNamesTheRain() {
        val forecast = forecast(weather = rainAt(day0, 16..17))
        val today = assertNotNull(advise(forecast, Load.LIGHT, dawn).first())

        assertEquals(Verdict.WILL_DRY, today.verdict)
        assertEquals(emptyList(), today.reasons)
        assertEquals(true, assertNotNull(today.bringIn) <= at(day0, 16 to 0))
        assertEquals(at(day0, 16 to 0), firstWet(forecast, day0, dawn))
        assertEquals(forecast.expectedFirstWet(day0, dawn), firstWet(forecast, day0, dawn))
    }
}
