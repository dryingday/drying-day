package io.github.dryingday.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Rule 2 of docs/bolts/02-drying-rules.md, each boundary tested on both sides. */
class HourScoringTest {
    @Suppress("LongParameterList") // A test builder: every parameter has a default and is passed by name.
    private fun hour(
        temperatureC: Double = 20.0,
        relativeHumidityPercent: Double = 50.0,
        windSpeedKmh: Double = 10.0,
        visibilityM: Double? = 20_000.0,
        precipitationProbabilityPercent: Double? = 0.0,
        precipitationMm: Double = 0.0,
        shortwaveRadiationWm2: Double = 0.0,
    ) = HourlyWeather(
        start = Instant.parse("2025-06-14T12:00:00Z"),
        temperatureC = temperatureC,
        relativeHumidityPercent = relativeHumidityPercent,
        windSpeedKmh = windSpeedKmh,
        visibilityM = visibilityM,
        precipitationProbabilityPercent = precipitationProbabilityPercent,
        precipitationMm = precipitationMm,
        windGustKmh = 20.0,
        shortwaveRadiationWm2 = shortwaveRadiationWm2,
    )

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.001) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun wetFromFortyPercentChance() {
        assertEquals(HourKind.DRYING, hour(precipitationProbabilityPercent = 39.9).kind())
        assertEquals(HourKind.WET, hour(precipitationProbabilityPercent = 40.0).kind())
    }

    @Test
    fun wetFromTwoTenthsOfAMillimetre() {
        assertEquals(HourKind.DRYING, hour(precipitationMm = 0.19).kind())
        assertEquals(HourKind.WET, hour(precipitationMm = 0.2).kind())
    }

    @Test
    fun precipitationAloneDecidesWhenProbabilityIsMissing() {
        assertEquals(HourKind.DRYING, hour(precipitationProbabilityPercent = null).kind())
        assertEquals(HourKind.WET, hour(precipitationProbabilityPercent = null, precipitationMm = 0.2).kind())
    }

    @Test
    fun dampFromNinetyFivePercentHumidity() {
        assertEquals(HourKind.DRYING, hour(relativeHumidityPercent = 94.9).kind())
        assertEquals(HourKind.DEW, hour(relativeHumidityPercent = 95.0).kind())
    }

    @Test
    fun dampBelowAKilometreVisibility() {
        assertEquals(HourKind.DRYING, hour(visibilityM = 1000.0).kind())
        assertEquals(HourKind.FOG, hour(visibilityM = 999.0).kind())
    }

    @Test
    fun missingVisibilityCountsAsClear() {
        assertEquals(HourKind.DRYING, hour(visibilityM = null).kind())
    }

    @Test
    fun rainOutranksFogAndFogOutranksDew() {
        assertEquals(HourKind.WET, hour(visibilityM = 500.0, precipitationMm = 1.0).kind())
        assertEquals(HourKind.FOG, hour(relativeHumidityPercent = 99.0, visibilityM = 500.0).kind())
    }

    @Test
    fun dewAndFogAreBothDamp() {
        assertEquals(listOf(HourKind.DEW, HourKind.FOG), HourKind.entries.filter { it.isDamp })
    }

    @Test
    fun everyTableIsInAscendingOrder() {
        for (table in listOf(DryingThresholds.VPD_POINTS, DryingThresholds.WIND_FACTOR, DryingThresholds.SUN_BONUS)) {
            assertTrue(table.zipWithNext().all { (a, b) -> a.from < b.from }, "out of order: $table")
        }
    }

    @Test
    fun vapourPressureDeficitFollowsMagnus() {
        // e_s(20 °C) = 2.338 kPa; at 50 % the deficit is half of it.
        assertClose(2.338, vapourPressureDeficitKpa(20.0, 0.0))
        assertClose(1.169, vapourPressureDeficitKpa(20.0, 50.0))
        assertClose(0.0, vapourPressureDeficitKpa(20.0, 100.0))
        // The spec's examples for the band edges.
        assertClose(0.206, vapourPressureDeficitKpa(6.0, 78.0))
        assertClose(1.005, vapourPressureDeficitKpa(20.0, 57.0))
        assertClose(1.521, vapourPressureDeficitKpa(25.0, 52.0))
        assertClose(2.503, vapourPressureDeficitKpa(30.0, 41.0))
    }

    @Test
    fun vpdBands() {
        assertEquals(0.0, vpdPoints(0.199))
        assertEquals(0.5, vpdPoints(0.2))
        assertEquals(0.5, vpdPoints(0.399))
        assertEquals(1.0, vpdPoints(0.4))
        assertEquals(1.0, vpdPoints(0.699))
        assertEquals(1.5, vpdPoints(0.7))
        assertEquals(1.5, vpdPoints(0.999))
        assertEquals(2.0, vpdPoints(1.0))
        assertEquals(2.0, vpdPoints(1.499))
        assertEquals(2.5, vpdPoints(1.5))
        assertEquals(2.5, vpdPoints(2.499))
        assertEquals(3.0, vpdPoints(2.5))
        assertEquals(3.0, vpdPoints(6.0))
    }

    @Test
    fun windBands() {
        assertEquals(0.6, windFactor(0.0))
        assertEquals(0.6, windFactor(4.9))
        assertEquals(1.0, windFactor(5.0))
        assertEquals(1.0, windFactor(14.9))
        assertEquals(1.3, windFactor(15.0))
        assertEquals(1.3, windFactor(29.9))
        assertEquals(1.5, windFactor(30.0))
        assertEquals(1.5, windFactor(80.0))
    }

    @Test
    fun sunBands() {
        assertEquals(0.0, sunBonus(299.0, vpdPoints = 1.0))
        assertEquals(0.25, sunBonus(300.0, vpdPoints = 1.0))
        assertEquals(0.25, sunBonus(599.0, vpdPoints = 1.0))
        assertEquals(0.5, sunBonus(600.0, vpdPoints = 1.0))
    }

    @Test
    fun noSunBonusInSaturatedAir() {
        assertEquals(0.0, sunBonus(900.0, vpdPoints = 0.0))
        assertEquals(0.5, sunBonus(900.0, vpdPoints = 0.5))
    }

    @Test
    fun pointsCombineTheTables() {
        // The spec's sunny day: 24 °C, 40 % (1.79 kPa), 20 km/h, strong sun: 2.5 × 1.3 + 0.5.
        assertClose(3.75, hour(24.0, 40.0, 20.0, shortwaveRadiationWm2 = 800.0).pointsPerHour())
        // Its grey day: 14 °C, 65 %, 20 km/h, weak sun: 1 × 1.3.
        assertClose(1.3, hour(14.0, 65.0, 20.0, shortwaveRadiationWm2 = 250.0).pointsPerHour())
        // Its cold damp day earns nothing, sun or not.
        assertClose(0.0, hour(6.0, 85.0, 15.0, shortwaveRadiationWm2 = 400.0).pointsPerHour())
    }

    @Test
    fun aPartialHourEarnsProRata() {
        val sunny = hour(24.0, 40.0, 20.0, shortwaveRadiationWm2 = 800.0)
        assertClose(3.75 / 4, sunny.points(15.minutes))
        assertClose(0.0, sunny.points(Duration.ZERO))
    }

    @Test
    fun wetAndDampHoursEarnNothing() {
        assertEquals(0.0, hour(precipitationMm = 1.0, shortwaveRadiationWm2 = 800.0).pointsPerHour())
        assertEquals(0.0, hour(visibilityM = 200.0, shortwaveRadiationWm2 = 800.0).pointsPerHour())
    }
}
