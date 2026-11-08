package io.github.dryingday.engine

import io.github.dryingday.reference.allReferenceCases
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Rules 3 to 5 of docs/bolts/02-drying-rules.md: load needs, windows, hang-out and bring-in, the verdict. */
class AdviseTest {
    @Test
    fun aSunnyDayDriesALightLoadByMidMorning() {
        val advice = assertNotNull(forecast().today())
        assertEquals(Verdict.WILL_DRY, advice.verdict)
        assertEquals(100, advice.dryness)
        assertEquals(at(day0, 8 to 0), advice.hangOut)
        assertNear(at(day0, 8 to 0) + 1.hours * (6 / 3.1), advice.bringIn)
    }

    @Test
    fun aHotDryDayDriesFasterThanAWarmOne() {
        // 30 °C at 30 % is 2.97 kPa: 3 × 1.3 + 0.5 = 4.4 points an hour, against 3.1 for the sunny fixture.
        val hot = W(30.0, 30.0)
        val advice = assertNotNull(forecast { _, _ -> hot }.today(Load.HEAVY))
        assertNear(at(day0, 8 to 0) + 1.hours * (12 / 4.4), advice.bringIn)
    }

    @Test
    fun aHeavyLoadNeedsTwiceThePoints() {
        val advice = assertNotNull(forecast().today(Load.HEAVY))
        assertNear(at(day0, 8 to 0) + 1.hours * (12 / 3.1), advice.bringIn)
    }

    @Test
    fun aShortGreyDayGetsAHeavyLoadPartlyDryAndBringsItInAtSunset() {
        // 8 hours at 1.3 points: 10.4 of 12, 86 %.
        val advice = assertNotNull(forecast(sunrise = 8 to 0, sunset = 16 to 0) { _, _ -> grey }.today(Load.HEAVY))
        assertEquals(Verdict.PARTLY_DRY, advice.verdict)
        assertEquals(86, advice.dryness)
        assertEquals(at(day0, 8 to 0), advice.hangOut)
        assertEquals(at(day0, 16 to 0), advice.bringIn)
    }

    @Test
    fun exactlyEnoughPointsIsDryDespiteRounding() {
        // 0.5 VPD points × 0.6 for still air = 0.3 an hour; twenty of them sum to 5.999999999999998 in doubles.
        val slow = W(15.0, 80.0, 2.0, 0.0) // VPD 0.34 kPa
        val advice = assertNotNull(
            forecast(sunrise = 0 to 0, sunset = 20 to 0) { _, _ -> slow }.today(
                now = at(
                    day0,
                    0 to 0,
                ),
            ),
        )
        assertEquals(Verdict.WILL_DRY to 100, advice.verdict to advice.dryness)
        assertNear(at(day0, 20 to 0), advice.bringIn)
        // The same 6 points are exactly half a heavy load's need: partly dry, not 49 %.
        val heavy =
            assertNotNull(
                forecast(sunrise = 0 to 0, sunset = 20 to 0) { _, _ -> slow }.today(
                    Load.HEAVY,
                    at(
                        day0,
                        0 to 0,
                    ),
                ),
            )
        assertEquals(Verdict.PARTLY_DRY to 50, heavy.verdict to heavy.dryness)
    }

    @Test
    fun eachLoadIsDryAtItsNeedAndNotJustBefore() {
        // From 08:00 at 1.3 points an hour: light needs 6 (4 h 37 min), heavy 12 (9 h 14 min).
        fun grey(load: Load, sunset: Pair<Int, Int>) = forecast(sunset = sunset) { _, _ -> grey }.today(load)
        assertEquals(Verdict.PARTLY_DRY to 97, grey(Load.LIGHT, 12 to 30)?.let { it.verdict to it.dryness })
        assertEquals(Verdict.WILL_DRY, grey(Load.LIGHT, 12 to 40)?.verdict)
        assertEquals(Verdict.PARTLY_DRY to 97, grey(Load.HEAVY, 17 to 0)?.let { it.verdict to it.dryness })
        assertEquals(Verdict.WILL_DRY, grey(Load.HEAVY, 17 to 30)?.verdict)
    }

    @Test
    fun aRawDayIsNotWorthIt() {
        val advice = assertNotNull(forecast { _, _ -> raw }.today())
        assertEquals(Verdict.DONT_BOTHER, advice.verdict)
        assertEquals(0, advice.dryness)
        assertNull(advice.hangOut)
        assertNull(advice.bringIn)
    }

    @Test
    fun halfDryIsPartlyAndJustUnderIsNot() {
        // From 08:00 at 1.3 points an hour; a heavy load needs 12, so 6 points is the line.
        val over = assertNotNull(forecast(sunset = 13 to 0) { _, _ -> grey }.today(Load.HEAVY))
        assertEquals(Verdict.PARTLY_DRY to 54, over.verdict to over.dryness) // 5 h, 6.5 points
        val under = assertNotNull(forecast(sunset = 12 to 30) { _, _ -> grey }.today(Load.HEAVY))
        assertEquals(Verdict.DONT_BOTHER to 48, under.verdict to under.dryness) // 4.5 h, 5.85 points
    }

    @Test
    fun rainEndsTheWindowAndTheLoadComesInBeforeIt() {
        val f = forecast { _, h -> if (h == 13) rain else grey }
        // Light: 6 points by 12:37, before the rain.
        val light = assertNotNull(f.today(Load.LIGHT))
        assertEquals(Verdict.WILL_DRY, light.verdict)
        assertNear(at(day0, 8 to 0) + 1.hours * (6 / 1.3), light.bringIn)
        // Heavy: 08–13 gets 6.5 points; 14–20 gets 7.8, the drier window, so that's the one.
        val heavy = assertNotNull(f.today(Load.HEAVY))
        assertEquals(Verdict.PARTLY_DRY, heavy.verdict)
        assertEquals(65, heavy.dryness)
        assertEquals(at(day0, 14 to 0), heavy.hangOut)
        assertEquals(at(day0, 20 to 0), heavy.bringIn)
    }

    @Test
    fun theWindowThatDriesSoonestWins() {
        // Grey until 11, rain at 11, sunny after: the sunny window dries a light load at 13:56, the grey one never.
        val advice = assertNotNull(
            forecast { _, h ->
                if (h == 11) {
                    rain
                } else if (h < 11) {
                    grey
                } else {
                    sunny
                }
            }.today(),
        )
        assertEquals(at(day0, 12 to 0), advice.hangOut)
        assertNear(at(day0, 12 to 0) + 1.hours * (6 / 3.1), advice.bringIn)
    }

    @Test
    fun aTieGoesToTheEarlierWindow() {
        // Two grey three-hour windows, split by rain, neither dry: 3.9 points each.
        val f = forecast(sunrise = 8 to 0, sunset = 15 to 0) { _, h -> if (h == 11) rain else grey }
        val advice = assertNotNull(f.today(Load.LIGHT))
        assertEquals(Verdict.PARTLY_DRY, advice.verdict)
        assertEquals(at(day0, 8 to 0), advice.hangOut)
        assertEquals(at(day0, 11 to 0), advice.bringIn)
    }

    @Test
    fun morningFogDelaysHangingOut() {
        val advice = assertNotNull(forecast { _, h -> if (h < 10) fog else sunny }.today())
        assertEquals(at(day0, 10 to 0), advice.hangOut)
    }

    @Test
    fun eveningDewEndsTheWindow() {
        val advice = assertNotNull(forecast { _, h -> if (h >= 17) dew else grey }.today(Load.HEAVY))
        assertEquals(at(day0, 17 to 0), advice.bringIn)
    }

    @Test
    fun hoursThatEarnNothingDontStartTheWindow() {
        val advice = assertNotNull(forecast { _, h -> if (h < 9) raw else sunny }.today())
        assertEquals(at(day0, 9 to 0), advice.hangOut)
    }

    @Test
    fun hangOutIsNeverBeforeNowOrSunrise() {
        val now = at(day0, 10 to 40)
        assertEquals(now, forecast().today(now = now)?.hangOut)
        val early = at(day0, 5 to 0)
        assertEquals(at(day0, 6 to 35), forecast(sunrise = 6 to 35).today(now = early)?.hangOut)
    }

    @Test
    fun aMidHourSunsetIsTheBringIn() {
        val advice = forecast(sunset = 15 to 20) { _, _ -> grey }.today(Load.HEAVY)
        assertEquals(at(day0, 15 to 20), advice?.bringIn)
    }

    @Test
    fun tomorrowAndTheDayAfterUseTheirWholeDaylight() {
        val days = advise(forecast { d, _ -> if (d == day0) raw else sunny }, Load.LIGHT, at(day0, 8 to 0))
        assertEquals(listOf(day0, LocalDate(2025, 6, 15), LocalDate(2025, 6, 16)), days.map { it?.date })
        assertEquals(Verdict.DONT_BOTHER, days[0]?.verdict)
        assertEquals(at(LocalDate(2025, 6, 15), 6 to 0), days[1]?.hangOut)
        assertEquals(at(LocalDate(2025, 6, 16), 6 to 0), days[2]?.hangOut)
    }

    @Test
    fun afterSunsetTodayIsOver() {
        val advice = assertNotNull(forecast().today(now = at(day0, 21 to 0)))
        assertEquals(Verdict.DONT_BOTHER, advice.verdict)
        assertEquals(0, advice.dryness)
    }

    @Test
    fun aDayWithoutDaylightDataGetsNoVerdict() {
        val days = advise(forecast(skipDays = setOf(LocalDate(2025, 6, 16))), Load.LIGHT, at(day0, 8 to 0))
        assertNotNull(days[1])
        assertNull(days[2])
    }

    @Test
    fun aMissingDaylightHourMeansNoVerdictButANightOneDoesnt() {
        val missingAtNoon = forecast(skipHours = setOf(at(LocalDate(2025, 6, 15), 12 to 0)))
        assertNull(advise(missingAtNoon, Load.LIGHT, at(day0, 8 to 0))[1])
        val missingAtNight = forecast(skipHours = setOf(at(LocalDate(2025, 6, 15), 2 to 0)))
        assertNotNull(advise(missingAtNight, Load.LIGHT, at(day0, 8 to 0))[1])
        // Today's hours before now don't matter either; the hour now falls in, or the last before sunset, do.
        assertNotNull(forecast(skipHours = setOf(at(day0, 7 to 0))).today())
        assertNull(forecast(skipHours = setOf(at(day0, 10 to 0))).today(now = at(day0, 10 to 40)))
        assertNull(forecast(skipHours = setOf(at(day0, 19 to 0))).today())
        // An hour only partly in daylight counts too.
        assertNull(forecast(sunrise = 6 to 35, skipHours = setOf(at(day0, 6 to 0))).today(now = at(day0, 5 to 0)))
    }

    @Test
    fun polarDayDriesAroundTheClockAndPolarNightNotAtAll() {
        // Asked at 22:00, two sunny hours are left before midnight: 6.2 points, enough for a light load.
        val polarDay = forecast(zone = oslo, polar = Polar.DAY)
        val late = assertNotNull(polarDay.today(now = at(day0, 22 to 0, oslo)))
        assertEquals(Verdict.WILL_DRY, late.verdict)
        assertNear(at(day0, 22 to 0, oslo) + 1.hours * (6 / 3.1), late.bringIn)
        val tomorrow = assertNotNull(advise(polarDay, Load.LIGHT, at(day0, 22 to 0, oslo))[1])
        assertEquals(at(LocalDate(2025, 6, 15), 0 to 0, oslo), tomorrow.hangOut)
        val polarNight = forecast(zone = oslo, polar = Polar.NIGHT)
        val advice = assertNotNull(polarNight.today(now = at(day0, 8 to 0, oslo)))
        assertEquals(Verdict.DONT_BOTHER, advice.verdict)
        assertEquals(0, advice.dryness)
    }

    @Test
    fun theThreeDaysAreLocalDatesAcrossClockChanges() {
        // The night before a 23-hour day, now + 24 h would skip a date; the day before a 25-hour day, repeat one.
        val cases = listOf(
            london to LocalDateTime(2025, 3, 29, 23, 30),
            london to LocalDateTime(2025, 10, 25, 0, 30),
            auckland to LocalDateTime(2025, 9, 27, 23, 30),
            auckland to LocalDateTime(2025, 4, 5, 0, 30),
        )
        for ((zone, local) in cases) {
            val first = local.date
            val days = advise(forecast(zone, first), Load.LIGHT, local.toInstant(zone))
            assertEquals((0..2).map { first.plus(it, DateTimeUnit.DAY) }, days.map { it?.date }, "$zone $local")
            // Tomorrow is the change day: sunrise at 06:00 local, dry 1 h 56 min later.
            val changeDay = assertNotNull(days[1])
            assertEquals(
                LocalDateTime(changeDay.date.year, changeDay.date.month, changeDay.date.day, 6, 0),
                changeDay.hangOut?.toLocalDateTime(zone),
                "$zone",
            )
            assertEquals(7 to 56, changeDay.bringIn?.toLocalDateTime(zone)?.let { it.hour to it.minute }, "$zone")
        }
    }

    @Test
    fun timesOnAClockChangeDayAreLocalTimes() {
        for ((zone, date) in listOf(london to LocalDate(2025, 3, 30), auckland to LocalDate(2025, 4, 6))) {
            val now = at(date, 8 to 0, zone)
            val advice = assertNotNull(advise(forecast(zone, date), Load.LIGHT, now).first())
            assertEquals(now, advice.hangOut)
            assertEquals(
                LocalDateTime(date.year, date.month, date.day, 9, 56),
                advice.bringIn?.toLocalDateTime(zone)?.let {
                    LocalDateTime(it.year, it.month, it.day, it.hour, it.minute)
                },
            )
        }
    }

    @Test
    fun everyReferenceCaseGetsThreeVerdictsForBothLoads() {
        // Real data must never trip an invariant or leave a day without a verdict. PR 6 checks the verdicts.
        for (case in allReferenceCases) {
            for (load in Load.entries) {
                val days = advise(case.forecast, load, case.now)
                assertTrue(days.all { it != null }, "${case.id} $load: $days")
            }
        }
    }
}
