package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Polar
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import io.github.dryingday.engine.advise
import io.github.dryingday.engine.at
import io.github.dryingday.engine.day0
import io.github.dryingday.engine.firstWet
import io.github.dryingday.engine.fog
import io.github.dryingday.engine.forecast
import io.github.dryingday.engine.grey
import io.github.dryingday.engine.oslo
import io.github.dryingday.engine.rain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val morning = at(day0, 5 to 0)
private val afterSunset = at(day0, 21 to 0)

private fun Forecast.adviceAt(index: Int, now: Instant = morning, load: Load = Load.LIGHT): DayAdvice? =
    advise(this, load, now)[index]

private const val RAIN_FROM_HOUR = 14
private const val SUNRISE = 8
private const val SUNSET = 16

// A short grey day, so a heavy load is partly dry: dry until the afternoon, then rain.
private fun partlyForecast() = forecast(sunrise = SUNRISE to 0, sunset = SUNSET to 0) { _, hour ->
    if (hour >= RAIN_FROM_HOUR) rain else grey
}

private fun Forecast.partlyAdvice() = checkNotNull(adviceAt(0, load = Load.HEAVY))

class NeverOutTest {
    @Test
    fun aNullDayIsNeverOut() {
        val forecast = forecast()

        assertTrue(neverOut(forecast, day0, null))
        assertNull(wetShare(forecast, day0, null))
    }

    @Test
    fun todayAfterSunsetIsNeverOut() {
        val forecast = forecast()
        val advice = checkNotNull(forecast.adviceAt(0, afterSunset))

        assertEquals(listOf(Reason.DAY_OVER), advice.reasons)
        assertTrue(neverOut(forecast, day0, advice))
        assertNull(wetShare(forecast, day0, advice))
    }

    @Test
    fun aDayWithNoUsableLightIsNeverOut() {
        val forecast = forecast(oslo, polar = Polar.NIGHT) { _, _ -> rain }
        val now = at(day0, 5 to 0, oslo)
        val advice = checkNotNull(forecast.adviceAt(0, now))

        assertTrue(neverOut(forecast, day0, advice))
        assertNull(wetShare(forecast, day0, advice))
    }

    @Test
    fun aDayWithAWindowOrJustRainWasOut() {
        val willDry = forecast()
        val wet = forecast { _, _ -> rain }

        assertFalse(neverOut(willDry, day0, willDry.adviceAt(0)))
        assertFalse(neverOut(wet, day0, wet.adviceAt(0)))
    }

    @Test
    fun dayOverPlusAnotherReasonKeepsTheRainLinesTest() {
        val forecast = forecast { _, _ -> rain }
        val advice = checkNotNull(forecast.adviceAt(0, afterSunset))

        assertEquals(Verdict.DONT_BOTHER, advice.verdict)
        assertEquals(advice.reasons == listOf(Reason.DAY_OVER), neverOut(forecast, day0, advice))
    }

    @Test
    fun theWetShareIsWhatsLeftAfterTheDryness() {
        val willDry = forecast()
        val partly = partlyForecast()
        val wet = forecast { _, _ -> rain }

        val dry = checkNotNull(willDry.adviceAt(0))
        val half = partly.partlyAdvice()
        val soaked = checkNotNull(wet.adviceAt(0))

        assertEquals(Verdict.WILL_DRY, dry.verdict)
        assertEquals(0, wetShare(willDry, day0, dry))
        assertEquals(Verdict.PARTLY_DRY, half.verdict)
        assertEquals(100 - half.dryness, wetShare(partly, day0, half))
        assertEquals(Verdict.DONT_BOTHER, soaked.verdict)
        assertEquals(100 - soaked.dryness, wetShare(wet, day0, soaked))
        assertTrue(checkNotNull(wetShare(wet, day0, soaked)) > 0)
    }

    @Test
    fun aNullDaySkyIsThePartlyDryOneWithNoPicture() {
        val sky = skyChoice(forecast(), day0, null, morning)

        assertEquals(SkyChoice(SkyKind.PARTLY_DRY, SkyPicture.NONE, rain = false), sky)
        assertEquals(sky, NO_FORECAST_SKY)
    }

    @Test
    fun eachVerdictHasItsSkyAndPicture() {
        val willDry = forecast()
        val partly = partlyForecast()
        val wet = forecast { _, _ -> rain }

        assertEquals(
            SkyChoice(SkyKind.WILL_DRY, SkyPicture.SUN, rain = false),
            skyChoice(willDry, day0, willDry.adviceAt(0), morning),
        )
        assertEquals(
            SkyChoice(SkyKind.PARTLY_DRY, SkyPicture.SUN_BEHIND_CLOUD, rain = false),
            skyChoice(partly, day0, partly.partlyAdvice(), morning),
        )
        assertEquals(
            SkyChoice(SkyKind.DONT_BOTHER, SkyPicture.RAIN_CLOUD, rain = true),
            skyChoice(wet, day0, wet.adviceAt(0), morning),
        )
    }

    @Test
    fun aFogDayHasTheDarkCloudButNoRain() {
        val forecast = forecast { _, _ -> fog }
        val advice = checkNotNull(forecast.adviceAt(0))

        assertEquals(Verdict.DONT_BOTHER, advice.verdict)
        assertNull(firstWet(forecast, day0, morning))
        assertEquals(
            SkyChoice(SkyKind.DONT_BOTHER, SkyPicture.RAIN_CLOUD, rain = false),
            skyChoice(forecast, day0, advice, morning),
        )
    }

    @Test
    fun todayAfterSunsetHasTheDarkCloudAndNoRain() {
        val forecast = forecast { _, _ -> rain }
        val advice = checkNotNull(forecast.adviceAt(0, afterSunset))

        assertEquals(
            SkyChoice(SkyKind.DONT_BOTHER, SkyPicture.RAIN_CLOUD, rain = false),
            skyChoice(forecast, day0, advice, afterSunset),
        )
    }

    @Test
    fun aPartlyDryDayWithRainComingGetsNoRain() {
        val forecast = partlyForecast()
        val advice = forecast.partlyAdvice()

        assertEquals(Verdict.PARTLY_DRY, advice.verdict)
        assertNotNull(firstWet(forecast, day0, morning))
        assertFalse(skyChoice(forecast, day0, advice, morning).rain)
    }
}
