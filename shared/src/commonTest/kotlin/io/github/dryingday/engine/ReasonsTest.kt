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
        assertEquals(
            Verdict.PARTLY_DRY to listOf(Reason.RAIN),
            reasons(
                forecast { _, h ->
                    if (h <
                        11
                    ) {
                        sunny
                    } else {
                        rain
                    }
                },
            ),
        )
    }

    @Test
    fun rainAllDay() {
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.RAIN), reasons(forecast { _, _ -> rain }))
    }

    @Test
    fun rainAtNightDoesNotCount() {
        val f = forecast { _, h -> if (h == 2) rain else raw }
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.COLD), reasons(f))
    }

    @Test
    fun fogEndingTheWindowOrAllDay() {
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.FOG), reasons(forecast { _, h -> if (h < 11) sunny else fog }))
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.FOG), reasons(forecast { _, _ -> fog }))
    }

    @Test
    fun dewEndingTheWindowIsDampAir() {
        // Grey 08–17 is 11.7 of 12 points.
        assertEquals(
            Verdict.PARTLY_DRY to listOf(Reason.DAMP_AIR),
            reasons(
                forecast { _, h ->
                    if (h <
                        17
                    ) {
                        grey
                    } else {
                        dew
                    }
                },
            ),
        )
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
    fun aRawDayWithNoWindowIsJudgedOverTheWholeDay() {
        assertEquals(Verdict.DONT_BOTHER to listOf(Reason.COLD), reasons(forecast { _, _ -> raw }))
    }

    @Test
    fun stillAirIsMoreThanHalfTheWindow() {
        // Sunset 18:00, so the window is 08–18. Exactly half still is not most; six hours of ten is.
        val half = forecast(sunset = 18 to 0) { _, h -> if (h < 13) stillGrey else grey }
        assertEquals(Verdict.PARTLY_DRY to emptyList(), reasons(half))
        val most = forecast(sunset = 18 to 0) { _, h -> if (h < 14) stillGrey else grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.STILL_AIR), reasons(most))
    }

    @Test
    fun shortDaylightIsUnderSixUsableHours() {
        val fiveHours = forecast(sunrise = 9 to 0, sunset = 14 to 0) { _, _ -> grey }
        assertEquals(Verdict.PARTLY_DRY to listOf(Reason.SHORT_DAYLIGHT), reasons(fiveHours))
        val sixHours = forecast(sunrise = 8 to 0, sunset = 14 to 0) { _, _ -> grey }
        assertEquals(Verdict.PARTLY_DRY to emptyList(), reasons(sixHours))
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
}
