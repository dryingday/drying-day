package io.github.dryingday.forecast

import io.github.dryingday.engine.Condition
import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.HourlyWeather
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Polar
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import io.github.dryingday.engine.W
import io.github.dryingday.engine.advise
import io.github.dryingday.engine.at
import io.github.dryingday.engine.auckland
import io.github.dryingday.engine.day0
import io.github.dryingday.engine.forecast
import io.github.dryingday.engine.london
import io.github.dryingday.engine.oslo
import io.github.dryingday.engine.rain
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_no_rain
import io.github.dryingday.shared.resources.forecast_rain_from
import io.github.dryingday.shared.resources.hour_degrees
import io.github.dryingday.shared.resources.hour_in_drying_window
import io.github.dryingday.shared.resources.hour_rain_chance
import io.github.dryingday.shared.resources.sky_partly_cloudy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val morning = at(day0, 5 to 0)

private fun Forecast.adviceFor(date: LocalDate, now: Instant = morning): DayAdvice? =
    advise(this, Load.LIGHT, now)[(date.toEpochDays() - now.toLocalDateTime(timeZone).date.toEpochDays()).toInt()]

private fun Forecast.columns(
    date: LocalDate = day0,
    now: Instant = morning,
    advice: DayAdvice? = adviceFor(date, now),
) = dayHours(this, date, advice, now, twentyFourHourFormat)

private fun Forecast.locals(date: LocalDate = day0, now: Instant = morning) = columns(date, now).map { it.time }

private fun windowAdvice(hangOut: Instant, bringIn: Instant) =
    DayAdvice(day0, Verdict.WILL_DRY, hangOut, bringIn, 100, emptyList(), emptyList(), emptyList())

private fun Forecast.mapHours(change: (HourlyWeather) -> HourlyWeather) = Forecast(timeZone, hours.map(change), days)

private fun hoursOf(first: Int, last: Int) = (first..last).map { it.toString().padStart(2, '0') + ":00" }

private fun Message.text() = resource.key + args

class DayHoursTest {
    @Test
    fun sunriseAndSunsetMidHourKeepTheirHours() {
        val forecast = forecast(sunrise = 6 to 30, sunset = 20 to 30)

        assertEquals(hoursOf(6, 20), forecast.locals())
    }

    @Test
    fun aSunsetOnTheHourAddsNoEmptyColumn() {
        assertEquals(hoursOf(6, 19), forecast(sunrise = 6 to 0, sunset = 20 to 0).locals())
    }

    @Test
    fun polarDayRunsMidnightToMidnight() {
        assertEquals(hoursOf(0, 23), forecast(oslo, polar = Polar.DAY).locals(now = at(day0, 0 to 0, oslo)))
    }

    @Test
    fun todayStartsWithTheHourThatContainsNow() {
        val forecast = forecast()

        assertEquals(hoursOf(10, 19), forecast.locals(now = at(day0, 10 to 20)))
        assertEquals(hoursOf(10, 19), forecast.locals(now = at(day0, 10 to 0)))
        assertEquals(hoursOf(6, 19), forecast.locals(day0.plus(1, DateTimeUnit.DAY), at(day0, 10 to 20)))
    }

    @Test
    fun aGapLeavesNoColumn() {
        val forecast = forecast(skipHours = setOf(at(day0, 10 to 0)))
        val columns = forecast.columns(advice = windowAdvice(at(day0, 9 to 0), at(day0, 12 to 0)))

        assertEquals(hoursOf(6, 9) + hoursOf(11, 19), columns.map { it.time })
    }

    @Test
    fun polarNightHasNoRow() {
        val forecast = forecast(oslo, polar = Polar.NIGHT)
        val now = at(day0, 5 to 0, oslo)

        assertNotNull(forecast.adviceFor(day0, now))
        assertEquals(emptyList(), forecast.columns(day0, now))
    }

    @Test
    fun todayAfterSunsetHasNoRow() {
        assertEquals(emptyList(), forecast().columns(now = at(day0, 21 to 0)))
        assertEquals(emptyList(), forecast().columns(now = at(day0, 20 to 0)))
    }

    @Test
    fun todayAfterAMidHourSunsetHasNoRow() {
        val forecast = forecast(sunset = 20 to 30)

        assertEquals(emptyList(), forecast.columns(now = at(day0, 20 to 45)))
        assertEquals(hoursOf(20, 20), forecast.locals(now = at(day0, 20 to 15)))
    }

    @Test
    fun aNullDayHasNoRow() {
        assertEquals(emptyList(), forecast().columns(advice = null))
    }

    @Test
    fun aDayWithoutDaylightRecordedHasNoRow() {
        val forecast = forecast(skipDays = setOf(day0))

        assertEquals(emptyList(), forecast.columns(advice = windowAdvice(at(day0, 9 to 0), at(day0, 12 to 0))))
    }

    @Test
    fun theClocksGoingForwardSkipAnHourOfLabels() {
        // London's clocks go forward at 01:00 UTC on 30 March 2025: the local day has 23 hours, no 01:00.
        val change = LocalDate(2025, 3, 30)
        val forecast = forecast(london, LocalDate(2025, 3, 29), polar = Polar.DAY)
        val labels = forecast.locals(change, at(LocalDate(2025, 3, 29), 22 to 0, london))

        assertEquals(hoursOf(0, 0) + hoursOf(2, 23), labels)
        assertEquals(
            Instant.parse("2025-03-30T00:00:00Z"),
            forecast.columns(change, at(change, 0 to 0, london)).first().start,
        )
    }

    @Test
    fun theClocksGoingBackRepeatAnHourOfLabelsInOrder() {
        // London's clocks go back at 01:00 UTC on 26 October 2025: the local day has 25 hours, 01:00 twice.
        val change = LocalDate(2025, 10, 26)
        val forecast = forecast(london, LocalDate(2025, 10, 25), polar = Polar.DAY)
        val columns = forecast.columns(change, at(LocalDate(2025, 10, 25), 22 to 0, london))

        assertEquals(hoursOf(0, 1) + hoursOf(1, 23), columns.map { it.time })
        assertEquals(
            "2025-10-25T23:00:00Z",
            columns[0].start.toString(),
        )
        assertEquals("2025-10-26T00:00:00Z", columns[1].start.toString())
        assertEquals("2025-10-26T01:00:00Z", columns[2].start.toString())
    }

    @Test
    fun theBarCoversTheHoursThatOverlapTheWindow() {
        val forecast = forecast()
        val advice = windowAdvice(at(day0, 9 to 30), at(day0, 14 to 30))
        val barred = forecast.columns(advice = advice).filter { it.inDryingWindow }.map { it.time }

        assertEquals(hoursOf(9, 14), barred)
    }

    @Test
    fun aWindowTouchingAnHourOnlyAtItsEdgeDoesNotMarkIt() {
        val forecast = forecast()
        val advice = windowAdvice(at(day0, 10 to 0), at(day0, 14 to 0))

        assertEquals(hoursOf(10, 13), forecast.columns(advice = advice).filter { it.inDryingWindow }.map { it.time })
    }

    @Test
    fun aDayWithoutAWindowHasNoBar() {
        val forecast = forecast { _, _ -> rain }
        val advice = forecast.adviceFor(day0)

        assertEquals(Verdict.DONT_BOTHER, advice?.verdict)
        assertEquals(emptyList(), forecast.columns(advice = advice).filter { it.inDryingWindow })
    }

    @Test
    fun theRainChanceShowsFromTenPercent() {
        val chances = listOf(null, 0.0, 9.9, 10.0, 40.0, 100.0)
        val forecast = forecast().mapHours {
            it.copy(
                precipitationProbabilityPercent = chances[it.start.toLocalDateTime(london).hour % chances.size],
            )
        }
        val shown = forecast.columns(advice = windowAdvice(at(day0, 9 to 0), at(day0, 12 to 0)))
            .associate { it.time to it.rainChance }

        assertNull(shown["06:00"]) // null
        assertNull(shown["07:00"]) // 0
        assertNull(shown["08:00"]) // 9.9
        assertEquals("10 %", shown["09:00"])
        assertEquals("40 %", shown["10:00"])
        assertEquals("100 %", shown["11:00"])
    }

    @Test
    fun theTemperatureRoundsHalfUp() {
        val temperatures = mapOf(
            10.5 to "11°",
            10.49 to "10°",
            11.0 to "11°",
            -0.5 to "0°",
            -0.51 to "-1°",
            -1.5 to "-1°",
            0.4 to "0°",
        )
        for ((degrees, text) in temperatures) {
            val forecast = forecast().mapHours { it.copy(temperatureC = degrees) }

            assertEquals(text, forecast.columns().first().temperature, "$degrees")
        }
    }

    @Test
    fun theConditionAndTimeComeFromTheHour() {
        val forecast = forecast(auckland).let { f ->
            Forecast(
                f.timeZone,
                f.hours.mapIndexed { i, h ->
                    h.copy(condition = Condition.FOG.takeIf { i % 2 == 1 })
                },
                f.days,
            )
        }
        val columns = forecast.columns(now = at(day0, 5 to 0, auckland))

        assertEquals("06:00", columns.first().time)
        assertEquals(listOf(null, Condition.FOG), columns.take(2).map { it.condition })
    }

    @Test
    fun theSpokenTextHasEveryPartTheColumnShows() {
        val column = HourColumn(at(day0, 10 to 0), "10:00", Condition.PARTLY_CLOUDY, "11°", "40 %", true)

        assertEquals(
            "10:00, ${Res.string.sky_partly_cloudy.key}[], ${Res.string.hour_degrees.key}[11], " +
                "${Res.string.hour_rain_chance.key}[40 %], ${Res.string.hour_in_drying_window.key}[]",
            column.spokenText { it.text() },
        )
    }

    @Test
    fun theSpokenTextLeavesOutEachPartTheColumnDoesNotShow() {
        val start = at(day0, 10 to 0)
        val degrees = "${Res.string.hour_degrees.key}[-3]"

        assertEquals("10:00, $degrees", HourColumn(start, "10:00", null, "-3°", null, false).spokenText { it.text() })
        assertEquals(
            "10:00, ${Res.string.sky_partly_cloudy.key}[], $degrees",
            HourColumn(start, "10:00", Condition.PARTLY_CLOUDY, "-3°", null, false).spokenText { it.text() },
        )
        assertEquals(
            "10:00, $degrees, ${Res.string.hour_rain_chance.key}[40 %]",
            HourColumn(start, "10:00", null, "-3°", "40 %", false).spokenText { it.text() },
        )
        assertEquals(
            "10:00, $degrees, ${Res.string.hour_in_drying_window.key}[]",
            HourColumn(start, "10:00", null, "-3°", null, true).spokenText { it.text() },
        )
    }

    @Test
    fun theSpokenTimeIsTheLabel() {
        val twelveHour: TimeFormat = { _, _ -> "10 am" }
        val forecast = forecast()
        val column = dayHours(forecast, day0, forecast.adviceFor(day0), morning, twelveHour).first()

        assertEquals("10 am", column.time)
        assertEquals("10 am", column.spokenText { it.text() }.substringBefore(","))
    }
}

class RainLineTest {
    private fun line(forecast: Forecast, now: Instant = morning, date: LocalDate = day0) =
        rainLine(forecast, date, forecast.adviceFor(date, now), now, twentyFourHourFormat)

    private fun rainAt(vararg hours: Int): (LocalDate, Int) -> W = { d, h ->
        if (d == day0 &&
            h in hours
        ) {
            rain
        } else {
            W()
        }
    }

    @Test
    fun saysWhenRainIsDue() {
        val message = assertNotNull(line(forecast(weather = rainAt(15, 16))))

        assertEquals(Res.string.forecast_rain_from.key, message.resource.key)
        assertEquals(listOf("15:00"), message.args)
    }

    @Test
    fun saysNoRainWhileItIsLight() {
        val message = assertNotNull(line(forecast()))

        assertEquals(Res.string.forecast_no_rain.key, message.resource.key)
        assertEquals(emptyList(), message.args)
    }

    @Test
    fun rainAfterSunsetIsNoRainWhileItIsLight() {
        val message = assertNotNull(line(forecast { _, h -> if (h >= 21) rain else W() }))

        assertEquals(Res.string.forecast_no_rain.key, message.resource.key)
    }

    @Test
    fun theTimeIsRoundedDownToTheMinute() {
        val now = at(day0, 10 to 20) + 45.seconds
        val message = assertNotNull(line(forecast(weather = rainAt(10)), now))

        assertEquals(listOf("10:20"), message.args)
    }

    @Test
    fun theTimeIsInTheForecastsZone() {
        val forecast = forecast(auckland, weather = rainAt(15))
        val message = assertNotNull(line(forecast, at(day0, 5 to 0, auckland)))

        assertEquals(listOf("15:00"), message.args)
        assertEquals("03:00", twentyFourHourFormat(at(day0, 15 to 0, auckland), TimeZone.UTC))
    }

    @Test
    fun aWillDryDayWithRainAfterBringInStillNamesIt() {
        val forecast = forecast(weather = rainAt(16, 17))

        assertEquals(Verdict.WILL_DRY, forecast.adviceFor(day0)?.verdict)
        assertEquals(listOf("16:00"), line(forecast)?.args)
    }

    @Test
    fun isLeftOutOnADayOverDay() {
        val forecast = forecast()
        val now = at(day0, 21 to 0)
        val advice = assertNotNull(forecast.adviceFor(day0, now))

        assertEquals(listOf(Reason.DAY_OVER), advice.reasons)
        assertNull(line(forecast, now))
    }

    @Test
    fun isLeftOutOnANullDay() {
        val forecast = forecast(skipHours = setOf(at(day0, 10 to 0)))

        assertNull(forecast.adviceFor(day0))
        assertNull(line(forecast))
    }

    @Test
    fun isLeftOutInPolarNight() {
        val forecast = forecast(oslo, polar = Polar.NIGHT) { _, _ -> rain }
        val now = at(day0, 5 to 0, oslo)

        assertNotNull(forecast.adviceFor(day0, now))
        assertNull(line(forecast, now))
    }
}
