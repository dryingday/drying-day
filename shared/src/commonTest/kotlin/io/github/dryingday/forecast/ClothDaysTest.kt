package io.github.dryingday.forecast

import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Verdict
import io.github.dryingday.engine.advise
import io.github.dryingday.engine.at
import io.github.dryingday.engine.day0
import io.github.dryingday.engine.forecast
import io.github.dryingday.engine.grey
import io.github.dryingday.engine.rain
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.day_today
import io.github.dryingday.shared.resources.day_tomorrow
import io.github.dryingday.shared.resources.forecast_day_missing
import io.github.dryingday.shared.resources.forecast_dryness
import io.github.dryingday.shared.resources.forecast_no_rain
import io.github.dryingday.shared.resources.forecast_rain_from
import io.github.dryingday.shared.resources.verdict_day_over
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val morning = at(day0, 5 to 0)
private val afterSunset = at(day0, 21 to 0)

private fun showing(forecast: Forecast, now: Instant, load: Load = Load.LIGHT) = ForecastStatus.Showing(
    fetchedAt = now,
    timeZone = forecast.timeZone,
    today = day0,
    days = advise(forecast, load, now),
    forecast = forecast,
    refreshing = false,
    lastError = null,
)

private fun List<Message>.keys() = map { it.resource }

class ClothDaysTest {
    @Test
    fun threeClothsWithTheirShortAndFullNames() {
        val cloths = clothDays(showing(forecast(), morning), morning, true, twentyFourHourFormat)

        assertEquals(listOf(0, 1, 2), cloths.map { it.index })
        assertEquals(Res.string.day_today, cloths[0].shortName)
        assertEquals(Res.string.day_today, cloths[0].fullName)
        assertEquals(Res.string.day_tomorrow, cloths[1].fullName)
        assertEquals(clothShortName(day0, 1), cloths[1].shortName)
        assertEquals(dayLabel(day0, 2), cloths[2].fullName)
    }

    @Test
    fun aWillDryClothIsPlainAndSaysNoRainIsExpected() {
        val cloth = clothDays(showing(forecast(), morning), morning, true, twentyFourHourFormat).first()

        assertEquals(Verdict.WILL_DRY, cloth.advice?.verdict)
        assertEquals(0, cloth.wetShare)
        assertEquals(listOf(Res.string.forecast_no_rain), cloth.sentence.keys())
        assertEquals(0, cloth.motion.spray)
    }

    @Test
    fun aPartlyDryClothSaysHowDryThenWhenTheRainComes() {
        val partly = forecast(sunrise = 8 to 0, sunset = 16 to 0) { _, hour -> if (hour >= 14) rain else grey }
        val cloth = clothDays(showing(partly, morning, Load.HEAVY), morning, true, twentyFourHourFormat).first()

        assertEquals(Verdict.PARTLY_DRY, cloth.advice?.verdict)
        assertEquals(listOf(Res.string.forecast_dryness, Res.string.forecast_rain_from), cloth.sentence.keys())
        assertEquals(100 - checkNotNull(cloth.advice).dryness, cloth.wetShare)
        assertEquals(3, cloth.motion.dripsOpen)
    }

    @Test
    fun aDontBotherClothSaysWhenTheRainComesAndIsWet() {
        val wet = forecast { _, _ -> rain }
        val cloth = clothDays(showing(wet, morning), morning, true, twentyFourHourFormat).first()

        assertEquals(Verdict.DONT_BOTHER, cloth.advice?.verdict)
        assertEquals(listOf(Res.string.forecast_rain_from), cloth.sentence.keys())
        assertEquals(100 - checkNotNull(cloth.advice).dryness, cloth.wetShare)
        assertEquals(26, cloth.motion.spray)
    }

    @Test
    fun todayAfterSunsetIsPlainSaysTooLateAndHasNoSentence() {
        val wet = forecast { _, _ -> rain }
        val cloth = clothDays(showing(wet, afterSunset), afterSunset, true, twentyFourHourFormat).first()

        assertEquals(Res.string.verdict_day_over, cloth.headline)
        assertEquals(emptyList(), cloth.sentence)
        assertNull(cloth.wetShare)
        assertEquals(0, cloth.motion.spray)
        assertEquals(0, cloth.motion.dripsOpen)
    }

    @Test
    fun aNullDaySaysThereIsntEnoughForecastAndIsPlain() {
        val short = forecast(skipDays = setOf(day0.plus(2, DateTimeUnit.DAY)))
        val cloth = clothDays(showing(short, morning), morning, true, twentyFourHourFormat)[2]

        assertNull(cloth.advice)
        assertNull(cloth.headline)
        assertEquals(listOf(Res.string.forecast_day_missing), cloth.sentence.keys())
        assertNull(cloth.wetShare)
        assertEquals(0, cloth.motion.dripsSmall)
    }

    @Test
    fun withAnimationsOffNothingShakesOrSprays() {
        val wet = forecast { _, _ -> rain }
        val cloth = clothDays(showing(wet, morning), morning, false, twentyFourHourFormat).first()

        assertNull(cloth.motion.shake)
        assertEquals(0, cloth.motion.spray)
        assertEquals(false, cloth.motion.dripsFall)
    }
}
