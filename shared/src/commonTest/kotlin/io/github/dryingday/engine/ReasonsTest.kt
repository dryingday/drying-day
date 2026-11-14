package io.github.dryingday.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Rule 6 of docs/bolts/02-drying-rules.md: what held drying back. Days run 06:00 to 20:00, asked at 08:00. */
class ReasonsTest {
    private val stillGrey = W(14.0, 65.0, 3.0, 250.0) // 1 × 0.6 = 0.6 points an hour

    private fun reasons(f: Forecast, load: Load = Load.HEAVY, now: kotlin.time.Instant = at(day0, 8 to 0, f.timeZone)) =
        assertNotNull(f.today(load, now)).let { it.verdict to it.reasons }

    @Test
    fun aDryDayHasNoReasons() {
        assertEquals(Verdict.WILL_DRY to emptyList(), reasons(forecast()))
    }

    @Test
    fun rainEndingTheWindow() {
        // Sunny 08–11 is 9.3 of 12 points, then rain.
        val f = forecast { _, h -> if (h < 11) sunny else rain }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.RAIN), reasons(f))
    }

    @Test
    fun rainAllDay() {
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.RAIN), reasons(forecast { _, _ -> rain }))
    }

    @Test
    fun rainBeforeTheWindowCountsToo() {
        // Rain until 15, then grey to sunset: 5 h, 6.5 of 12 points.
        val f = forecast { _, h -> if (h < 15) rain else grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.RAIN), reasons(f))
    }

    @Test
    fun morningFogThatDelaysHangingOutCounts() {
        val f = forecast { _, h -> if (h < 13) fog else grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.FOG), reasons(f))
    }

    @Test
    fun dewAllDayIsDampAirEvenWithNoWindow() {
        // 8 °C at 96 %: dew every hour, so there's no window; and cold.
        val f = forecast { _, _ -> W(8.0, 96.0) }
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.COLD, Reason.DAMP_AIR), reasons(f))
    }

    @Test
    fun rainAfterTheWindowDoesNotCount() {
        // Grey 08–17 (97 %), ended by dew at 17; the rain from 18 comes after the window.
        val f = forecast { _, h ->
            if (h < 17) {
                grey
            } else if (h == 17) {
                dew
            } else {
                rain
            }
        }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.DAMP_AIR), reasons(f))
    }

    @Test
    fun rainAtNightDoesNotCount() {
        val f = forecast { _, h -> if (h == 2) rain else raw }
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.COLD), reasons(f))
    }

    @Test
    fun fogEndingTheWindowOrAllDay() {
        val f = forecast { _, h -> if (h < 11) sunny else fog }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.FOG), reasons(f))
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.FOG), reasons(forecast { _, _ -> fog }))
    }

    @Test
    fun dewEndingTheWindowIsDampAir() {
        // Grey 08–17 is 11.7 of 12 points.
        val f = forecast { _, h -> if (h < 17) grey else dew }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.DAMP_AIR), reasons(f))
    }

    @Test
    fun coldAndDampAirAreSplitAtTenDegrees() {
        // Below 0.4 kPa all day. 9.9 °C at 70 % and 15 °C at 88 % both earn 0.5 points an hour with a 10 km/h breeze.
        val cold = W(9.9, 70.0, 10.0, 0.0) // 0.37 kPa
        val mild = W(15.0, 88.0, 10.0, 0.0) // 0.20 kPa
        assertEquals(listOf(Reason.COLD), reasons(forecast { _, _ -> cold }).second)
        assertEquals(listOf(Reason.DAMP_AIR), reasons(forecast { _, _ -> mild }).second)
        // At 10 °C it's damp air, not cold.
        assertEquals(listOf(Reason.DAMP_AIR), reasons(forecast { _, _ -> W(10.0, 70.0, 10.0, 0.0) }).second)
    }

    @Test
    fun justAboveTheSlowLineIsNeitherColdNorDamp() {
        // 10 °C at 67 % is 0.405 kPa: 1 point an hour with a 10 km/h breeze, 11 of 12 for a heavy load by 19:00.
        val f = forecast(sunset = 19 to 0) { _, _ -> W(10.0, 67.0, 10.0, 0.0) }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.SHORT_DAYLIGHT), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun theWholeWindowCountsNotJustFromHangOut() {
        // Raw 08–15 earns nothing but is drying time in the window; grey 15–20 earns 6.5 of 12. The cold
        // morning is most of the window.
        val f = forecast { _, h -> if (h < 15) raw else grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.COLD), reasons(f))
    }

    @Test
    fun aRawDayWithNoWindowIsJudgedOverTheWholeDay() {
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.COLD), reasons(forecast { _, _ -> raw }))
    }

    @Test
    fun stillAirIsMoreThanHalfTheWindow() {
        // Sunset 18:00, so the window is 08–18. Exactly half still is not most; six hours of ten is.
        val half = forecast(sunset = 18 to 0) { _, h -> if (h < 13) stillGrey else grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.STILL_AIR), reasons(half))
        assertEquals(emptyList(), table(half))
        val most = forecast(sunset = 18 to 0) { _, h -> if (h < 14) stillGrey else grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.STILL_AIR), reasons(most))
    }

    @Test
    fun shortDaylightIsUnderSixUsableHours() {
        val fiveHours = forecast(sunrise = 9 to 0, sunset = 14 to 0) { _, _ -> grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.SHORT_DAYLIGHT), reasons(fiveHours))
        val sixHours = forecast(sunrise = 8 to 0, sunset = 14 to 0) { _, _ -> grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.SHORT_DAYLIGHT), reasons(sixHours))
        assertEquals(emptyList(), table(sixHours))
        // Today it counts from now.
        assertEquals(listOf(Reason.SHORT_DAYLIGHT), reasons(forecast { _, _ -> grey }, now = at(day0, 15 to 0)).second)
    }

    @Test
    fun afterSunsetTheDayIsOverAndThatIsAll() {
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.DAY_OVER), reasons(forecast(), now = at(day0, 21 to 0)))
    }

    @Test
    fun polarNightIsShortDaylightNotDayOver() {
        val f = forecast(zone = oslo, polar = Polar.NIGHT)
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.SHORT_DAYLIGHT), reasons(f, now = at(day0, 8 to 0, oslo)))
    }

    @Test
    fun everyReasonThatAppliesInOrder() {
        // 09–14 daylight, 8 °C at 70 % (0.32 kPa) in still air, rain from 12.
        val chilly = W(8.0, 70.0, 3.0, 0.0)
        val f = forecast(sunrise = 9 to 0, sunset = 14 to 0) { _, h -> if (h < 12) chilly else rain }
        val expected = listOf(Reason.RAIN, Reason.COLD, Reason.STILL_AIR, Reason.SHORT_DAYLIGHT)
        assertEquals(Verdict.DONT_BOTHER to expected, reasons(f))
    }

    @Test
    fun aLightLoadThatIsPartlyDryGetsReasonsToo() {
        // Sunny 08–09 is 3.1 of 6 points, then rain.
        val f = forecast { _, h -> if (h < 9) sunny else rain }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.RAIN), reasons(f, Load.LIGHT))
    }

    // Rule 7. Hour types, each at 1.5 points or less an hour so the days stay short of a full load:
    // cold is cold and slow (6 °C, 0.14 kPa, wind factor 1.3), damp warm and slow (0.20 kPa, factor 1.0),
    // soggy warm and slower still (0.17 kPa, no points), still not slow but with a wind factor of 0.6,
    // fair and good neither slow nor still (1 and 1.5 points).
    private val cold = raw
    private val damp = W(15.0, 88.0, 10.0, 0.0)
    private val soggy = W(15.0, 90.0, 10.0, 0.0)
    private val still = W(22.0, 50.0, 3.0, 0.0)
    private val fair = W(10.0, 67.0, 10.0, 0.0)
    private val good = W(15.0, 58.0, 10.0, 0.0)

    /** Weather by local hour from 08:00, for as many hours as each part says, in turn; the rest is sunny. */
    private fun layout(vararg parts: Pair<Int, W>): (kotlinx.datetime.LocalDate, Int) -> W {
        val byHour = mutableMapOf<Int, W>()
        var hour = FIRST_HOUR
        for ((count, weather) in parts) repeat(count) { byHour[hour++] = weather }
        return { _, h -> byHour[h] ?: sunny }
    }

    private fun day(sunset: Pair<Int, Int>, vararg parts: Pair<Int, W>) =
        forecast(sunset = sunset, weather = layout(*parts))

    private fun table(f: Forecast, load: Load = Load.HEAVY, now: kotlin.time.Instant = at(day0, 8 to 0, f.timeZone)) =
        assertNotNull(tableReasons(f, load, now).first())

    @Test
    fun theColdestFactorIsNamedWhenNothingCrossesItsLine() {
        // Closeness over 12 usable hours: COLD 5 h = 0.83, DAMP_AIR 3 h = 0.5, STILL_AIR 2 h = 0.33,
        // SHORT_DAYLIGHT 6 / 12 = 0.5. 5.9 of 12 points is 49 %.
        val f = day(20 to 0, 5 to cold, 3 to damp, 2 to still, 2 to fair)
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.COLD), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun theDampestFactorIsNamed() {
        // DAMP_AIR 5 h = 0.83, COLD 3 h = 0.5, STILL_AIR 2 h = 0.33, SHORT_DAYLIGHT 0.5. 6.9 of 12 points.
        val f = day(20 to 0, 5 to damp, 3 to cold, 2 to still, 2 to fair)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.DAMP_AIR), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun theStillestFactorIsNamed() {
        // STILL_AIR 5 h = 0.83, COLD 3 h = 0.5, DAMP_AIR 3 h = 0.5, SHORT_DAYLIGHT 0.5. 8.5 of 12 points.
        val f = day(20 to 0, 5 to still, 3 to cold, 3 to damp, 1 to fair)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.STILL_AIR), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun shortDaylightIsNamedWhenItIsTheClosest() {
        // 8 usable hours, light load: SHORT_DAYLIGHT 6 / 8 = 0.75, COLD, DAMP_AIR and STILL_AIR 2 h = 0.5 each.
        // 5.4 of 6 points is 90 %.
        val f = day(16 to 0, 2 to cold, 2 to damp, 2 to still, 2 to fair)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.SHORT_DAYLIGHT), reasons(f, Load.LIGHT))
        assertEquals(emptyList(), table(f, Load.LIGHT))
    }

    @Test
    fun aTieBetweenColdAndDampAirGoesToCold() {
        // COLD 4 h = 0.67, DAMP_AIR 4 h = 0.67, STILL_AIR 2 h = 0.33, SHORT_DAYLIGHT 0.5. 6.4 of 12 points.
        val f = day(20 to 0, 4 to damp, 4 to cold, 2 to still, 2 to fair)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.COLD), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun aTieBetweenDampAirAndStillAirGoesToDampAir() {
        // DAMP_AIR 4 h = 0.67, STILL_AIR 4 h = 0.67, COLD 2 h = 0.33, SHORT_DAYLIGHT 0.5. 8.8 of 12 points.
        val f = day(20 to 0, 4 to still, 4 to damp, 2 to cold, 2 to fair)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.DAMP_AIR), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun aTieBetweenStillAirAndShortDaylightGoesToStillAir() {
        // A quarter of 12 usable hours in still air: STILL_AIR 3 h = 0.5, SHORT_DAYLIGHT 6 / 12 = 0.5,
        // COLD and DAMP_AIR 2 h = 0.33. 9.6 of 12 points.
        val f = day(20 to 0, 3 to still, 2 to cold, 2 to damp, 5 to fair)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.STILL_AIR), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun aDontBotherDayWithAWindowAndAnEmptyListGetsOneReason() {
        // One sunny hour (3.1 of 12 points), 5 cold and 6 warm hours that earn nothing. DAMP_AIR is half the
        // window, which is not most: 1.0. COLD 5 h = 0.83; wind is under 5 km/h in no hour at all.
        val f = day(20 to 0, 1 to sunny, 5 to cold, 6 to soggy)
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.DAMP_AIR), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun aDontBotherDayWithNoWindowAndAnEmptyListGetsColdByTheTieBreak() {
        // 6 cold and 6 warm hours, none earning anything, so there is no window: each is exactly half the
        // usable time, 1.0, and COLD comes first in the table's order.
        val f = day(20 to 0, 6 to cold, 6 to soggy)
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.COLD), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    @Test
    fun exactlyHalfColdComesFromRuleSevenAndOneHourMoreFromTheTable() {
        val half = day(20 to 0, 6 to cold, 6 to good)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.COLD), reasons(half))
        assertEquals(emptyList(), table(half))
        val more = day(20 to 0, 7 to cold, 5 to good)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.COLD), reasons(more))
        assertEquals(listOf(Reason.COLD), table(more))
    }

    @Test
    fun shortDaylightIsJudgedOnTheUsableTimeFromNow() {
        // Asked at 11:00 with 9 usable hours: SHORT_DAYLIGHT 6 / 9 = 0.67, COLD, DAMP_AIR and STILL_AIR 2 h
        // = 0.44 each (over the whole 14 h of daylight, 6 / 14 = 0.43 would lose to them). The rain before
        // 11:00 is gone. 7.9 of 12 points.
        val f = day(20 to 0, 3 to rain, 2 to cold, 2 to damp, 2 to still, 3 to good)
        val now = at(day0, 11 to 0)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.SHORT_DAYLIGHT), reasons(f, now = now))
        assertEquals(emptyList(), table(f, now = now))
    }

    @Test
    fun sharesAreCountedByTimeWhenTheSunsetFallsMidHour() {
        // Sunset 15:30: 7.5 usable hours. Cold time is 08–10 and the last half hour, 2.5 h: COLD 0.67, which
        // SHORT_DAYLIGHT's 6 / 7.5 = 0.8 beats. Counting the last hour whole would make COLD 0.8, a tie that
        // COLD wins.
        val f = day(15 to 30, 2 to cold, 5 to good, 1 to cold)
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.SHORT_DAYLIGHT), reasons(f))
        assertEquals(emptyList(), table(f))
    }

    private companion object {
        const val FIRST_HOUR = 8
    }
}
