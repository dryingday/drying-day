package io.github.dryingday.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

/** The indoor plan (docs/bolts/02-drying-rules.md, "Tips and indoor plan"). Days 06:00–20:00, asked at 08:00. */
class IndoorPlanTest {
    private val airer = IndoorStep.AIRER_SPACED
    private val window = IndoorStep.OPEN_WINDOW
    private val dehumidifier = IndoorStep.DEHUMIDIFIER_OR_CLOSED_ROOM
    private val radiator = IndoorStep.NOT_ON_RADIATOR
    private val turn = IndoorStep.TURN_HEAVY_ITEMS

    private fun plan(f: Forecast, load: Load = Load.HEAVY, now: Instant = at(day0, 8 to 0, f.timeZone)) =
        assertNotNull(f.today(load, now)).let { it.verdict to it.indoorPlan }

    /** Sunny 08–11, then rain at [humidity] %: a heavy load comes in partly dry at 11:00. */
    private fun rainFrom11(humidity: Double): Forecast {
        val wet = W(relativeHumidityPercent = humidity, precipitationMm = 2.0)
        return forecast { _, h -> if (h < 11) sunny else wet }
    }

    @Test
    fun willDryNeedsNoIndoorPlan() {
        assertEquals(Verdict.WILL_DRY to emptyList(), plan(forecast()))
    }

    @Test
    fun partlyDryFinishesIndoorsByTheAirAfterBringIn() {
        assertEquals(Verdict.PARTLY_DRY to listOf(airer, window, radiator, turn), plan(rainFrom11(50.0)))
        assertEquals(Verdict.PARTLY_DRY to listOf(airer, dehumidifier, radiator, turn), plan(rainFrom11(90.0)))
    }

    @Test
    fun seventyPercentIsTheLine() {
        assertEquals(listOf(airer, window, radiator, turn), plan(rainFrom11(69.9)).second)
        assertEquals(listOf(airer, dehumidifier, radiator, turn), plan(rainFrom11(70.0)).second)
    }

    @Test
    fun dontBotherUsesTheDaysUsableTime() {
        // Raw air at 85 %; a light load, so nothing to turn.
        val f = forecast { _, _ -> raw }
        assertEquals(Verdict.DONT_BOTHER to listOf(airer, dehumidifier, radiator), plan(f, Load.LIGHT))
    }

    @Test
    fun afterSunsetItIsTheNextSixHours() {
        // From 21:00 to 03:00 at 60 %, humid otherwise.
        val (dry, humid) = W(relativeHumidityPercent = 60.0) to W(relativeHumidityPercent = 90.0)
        val f = forecast { _, h -> if (h >= 21 || h < 3) dry else humid }
        assertEquals(Verdict.DONT_BOTHER to listOf(airer, window, radiator), plan(f, Load.LIGHT, at(day0, 21 to 0)))
    }

    @Test
    fun bringingInAtSunsetAlsoUsesTheNextSixHours() {
        // Grey 08–16 with a 16:00 sunset: partly dry, in at sunset; humid evening.
        val humid = W(relativeHumidityPercent = 90.0)
        val f = forecast(sunrise = 8 to 0, sunset = 16 to 0) { _, h -> if (h < 16) grey else humid }
        assertEquals(Verdict.PARTLY_DRY to listOf(airer, dehumidifier, radiator, turn), plan(f))
    }

    @Test
    fun withNoForecastForTheIndoorTimeNeitherAirStepIsGiven() {
        val missing = (16..21).map { at(day0, it to 0) }.toSet()
        val f = forecast(sunrise = 8 to 0, sunset = 16 to 0, skipHours = missing) { _, _ -> grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(airer, radiator, turn), plan(f))
    }
}
