package io.github.dryingday.engine

import io.github.dryingday.reference.allReferenceCases
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Synthetic forecasts for the engine tests: three days of hours with weather by local date and hour. */

internal val london = TimeZone.of("Europe/London")
internal val auckland = TimeZone.of("Pacific/Auckland")
internal val oslo = TimeZone.of("Europe/Oslo")
internal val day0 = LocalDate(2025, 6, 14)

/** Weather for one hour, by its local start time. */
internal data class W(
    val temperatureC: Double = 22.0,
    val relativeHumidityPercent: Double = 50.0,
    val windSpeedKmh: Double = 20.0,
    val shortwaveRadiationWm2: Double = 800.0,
    val precipitationMm: Double = 0.0,
    val visibilityM: Double? = null,
)

// Points an hour. The grey and raw days are the spec's worked examples; sunny is kept below 1.5 kPa so
// these timing tests don't depend on the top of the VPD table, which has its own test below.
internal val sunny = W() // 1.32 kPa: 2 × 1.3 + 0.5 = 3.1
internal val grey = W(14.0, 65.0, 20.0, 250.0) // 1 × 1.3 = 1.3
internal val raw = W(6.0, 85.0, 15.0, 200.0) // 0
internal val rain = W(precipitationMm = 2.0)
internal val dew = W(12.0, 96.0)
internal val fog = W(visibilityM = 300.0)

internal enum class Polar { NONE, DAY, NIGHT }

/**
 * Three days from [first], every hour of them, with [weather] by local date and hour. Sunrise and sunset
 * are local clock times, the same each day, unless [polar] makes it polar day or night.
 */
internal fun forecast(
    zone: TimeZone = london,
    first: LocalDate = day0,
    sunrise: Pair<Int, Int> = 6 to 0,
    sunset: Pair<Int, Int> = 20 to 0,
    polar: Polar = Polar.NONE,
    skipDays: Set<LocalDate> = emptySet(),
    skipHours: Set<Instant> = emptySet(),
    weather: (LocalDate, Int) -> W = { _, _ -> sunny },
): Forecast {
    val dates = (0..2).map { first.plus(it, DateTimeUnit.DAY) }
    val start = first.atStartOfDayIn(zone)
    val end = first.plus(3, DateTimeUnit.DAY).atStartOfDayIn(zone)
    val hours = generateSequence(start) { it + 1.hours }.takeWhile { it < end }.filter { it !in skipHours }.map {
        val local = it.toLocalDateTime(zone)
        val w = weather(local.date, local.hour)
        HourlyWeather(
            start = it,
            temperatureC = w.temperatureC,
            relativeHumidityPercent = w.relativeHumidityPercent,
            windSpeedKmh = w.windSpeedKmh,
            visibilityM = w.visibilityM,
            precipitationProbabilityPercent = null,
            precipitationMm = w.precipitationMm,
            windGustKmh = w.windSpeedKmh,
            shortwaveRadiationWm2 = w.shortwaveRadiationWm2,
        )
    }.toList()
    val days = dates.filter { it !in skipDays }.map { date ->
        val midnight = date.atStartOfDayIn(zone)
        val nextMidnight = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        when (polar) {
            Polar.NONE -> DailyLight(date, at(date, sunrise, zone), at(date, sunset, zone))
            Polar.DAY -> DailyLight(date, midnight, nextMidnight)
            Polar.NIGHT -> DailyLight(date, midnight, midnight)
        }
    }
    return Forecast(zone, hours, days)
}

internal fun at(date: LocalDate, hm: Pair<Int, Int>, zone: TimeZone = london) =
    LocalDateTime(date.year, date.month, date.day, hm.first, hm.second).toInstant(zone)

internal fun Forecast.today(load: Load = Load.LIGHT, now: Instant = at(day0, 8 to 0, timeZone)) =
    advise(this, load, now).first()

internal fun assertNear(expected: Instant, actual: Instant?, tolerance: Duration = 1.seconds) {
    assertNotNull(actual)
    assertTrue(
        abs((expected - actual).inWholeMilliseconds) <= tolerance.inWholeMilliseconds,
        "$expected vs $actual",
    )
}
