package io.github.dryingday.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Hanging tips (docs/bolts/02-drying-rules.md, "Tips and indoor plan"). Days 06:00–20:00, asked at 08:00. */
class TipsTest {
    private fun tips(f: Forecast, load: Load = Load.LIGHT) = assertNotNull(f.today(load)).tips

    @Test
    fun aCalmDryDayNeedsNoTips() {
        // A light load is on the line 08:00–09:56, under two hours of strong sun.
        assertEquals(emptyList(), tips(forecast()))
    }

    @Test
    fun gustsFromFortyKilometresAnHour() {
        assertEquals(listOf(Tip.PEG_WELL), tips(forecast { _, _ -> W(gustKmh = 40.0) }))
        assertEquals(emptyList(), tips(forecast { _, _ -> W(gustKmh = 39.9) }))
    }

    @Test
    fun aTwentyToThirtyNinePercentChanceOfShowers() {
        assertEquals(emptyList(), tips(forecast { _, _ -> W(rainChancePercent = 19.9) }))
        assertEquals(listOf(Tip.SHOWERS_POSSIBLE), tips(forecast { _, _ -> W(rainChancePercent = 20.0) }))
        assertEquals(listOf(Tip.SHOWERS_POSSIBLE), tips(forecast { _, _ -> W(rainChancePercent = 39.9) }))
    }

    @Test
    fun heavyItemsForAHeavyLoad() {
        // On the line 08:00–11:52, so also nearly four hours of strong sun.
        assertEquals(listOf(Tip.HEAVY_ITEMS, Tip.DARKS_INSIDE_OUT), tips(forecast(), Load.HEAVY))
    }

    @Test
    fun spaceOutInStillAir() {
        // 0.6 points an hour: on the line 08:00–18:00, all of it still.
        assertEquals(listOf(Tip.SPACE_OUT), tips(forecast { _, _ -> W(14.0, 65.0, 3.0, 250.0) }))
    }

    @Test
    fun spaceOutIsForMoreThanHalfTheTimeOnTheLine() {
        // A heavy load out 08:00 to an 18:00 sunset, partly dry: still air for exactly half, then six hours of ten.
        val still = W(14.0, 65.0, 3.0, 250.0)
        val half = forecast(sunset = 18 to 0) { _, h -> if (h < 13) still else grey }
        assertEquals(listOf(Tip.HEAVY_ITEMS), tips(half, Load.HEAVY))
        val most = forecast(sunset = 18 to 0) { _, h -> if (h < 14) still else grey }
        assertEquals(listOf(Tip.HEAVY_ITEMS, Tip.SPACE_OUT), tips(most, Load.HEAVY))
    }

    @Test
    fun darksInsideOutAfterTwoHoursOfStrongSun() {
        // Grey with sun: 1.8 points an hour, so a light load is out 08:00–11:20.
        fun sun(strongUntil: Int, strong: Double = 700.0) =
            forecast { _, h -> W(14.0, 65.0, 20.0, if (h < strongUntil) strong else 650.0) }
        assertEquals(listOf(Tip.DARKS_INSIDE_OUT), tips(sun(strongUntil = 10)))
        assertEquals(emptyList(), tips(sun(strongUntil = 9)))
        assertEquals(emptyList(), tips(sun(strongUntil = 24, strong = 699.0)))
    }

    @Test
    fun atMostThreeInPriorityOrder() {
        // Still, gusty, showery and sunny, heavy: every tip applies; the first three are kept.
        val f = forecast { _, _ -> W(14.0, 65.0, 3.0, 800.0, rainChancePercent = 30.0, gustKmh = 45.0) }
        assertEquals(listOf(Tip.PEG_WELL, Tip.SHOWERS_POSSIBLE, Tip.HEAVY_ITEMS), tips(f, Load.HEAVY))
    }

    @Test
    fun onlyTheTimeOnTheLineCounts() {
        // Gusts from noon, after a light load is in.
        assertEquals(emptyList(), tips(forecast { _, h -> W(gustKmh = if (h >= 12) 50.0 else 20.0) }))
    }

    @Test
    fun partlyDryGetsTipsAndDontBotherNone() {
        // Gusty sun 08–11, then rain: partly dry.
        val partly = forecast { _, h -> if (h < 11) W(gustKmh = 45.0) else rain }
        assertEquals(listOf(Tip.PEG_WELL, Tip.HEAVY_ITEMS, Tip.DARKS_INSIDE_OUT), tips(partly, Load.HEAVY))
        assertEquals(emptyList(), tips(forecast { _, _ -> raw.copy(gustKmh = 50.0) }))
    }
}
