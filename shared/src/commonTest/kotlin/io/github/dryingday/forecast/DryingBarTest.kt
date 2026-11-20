package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Polar
import io.github.dryingday.engine.Verdict
import io.github.dryingday.engine.advise
import io.github.dryingday.engine.at
import io.github.dryingday.engine.auckland
import io.github.dryingday.engine.day0
import io.github.dryingday.engine.forecast
import io.github.dryingday.engine.grey
import io.github.dryingday.engine.london
import io.github.dryingday.engine.rain
import io.github.dryingday.reference.allReferenceCases
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_bar_label
import io.github.dryingday.shared.resources.forecast_bar_single
import io.github.dryingday.shared.resources.forecast_dryness
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val WIDTH = 50f
private val kathmandu = TimeZone.of("Asia/Kathmandu")

/** Columns for the local hours [first] to [last] on [day0] in London, summer time. */
private fun columns(first: Int = 8, last: Int = 12): List<HourColumn> =
    (first..last).map { HourColumn(at(day0, it to 0), it.toString(), null, false) }

private fun window(from: Pair<Int, Int>, to: Pair<Int, Int>) = Window(at(day0, from), at(day0, to))

private fun span(columns: List<HourColumn>, window: Window?) = barSpan(columns, window, WIDTH, columns.size * WIDTH)

private fun advice(verdict: Verdict, hangOut: Instant?, bringIn: Instant?, dryness: Int = 100) =
    DayAdvice(day0, verdict, hangOut, bringIn, dryness, emptyList(), emptyList(), emptyList())

private fun BarSpan?.pair() = this?.let { it.start to it.end }

class DryingBarTest {
    @Test
    fun midHourTimesLandAtTheirFractionOfTheColumn() {
        // 09:15 is a quarter into the second column, 10:45 three quarters into the third.
        assertEquals((1.25f * WIDTH) to (2.75f * WIDTH), span(columns(), window(9 to 15, 10 to 45)).pair())
    }

    @Test
    fun timesOnTheHourLandOnTheColumnEdges() {
        assertEquals((1 * WIDTH) to (3 * WIDTH), span(columns(), window(9 to 0, 11 to 0)).pair())
    }

    @Test
    fun aBringInAtTheEndOfTheLastHourIsTheRowsRightEdge() {
        assertEquals((3 * WIDTH) to (5 * WIDTH), span(columns(), window(11 to 0, 13 to 0)).pair())
    }

    @Test
    fun aWindowInOneColumnKeepsItsLengthWhenWideEnough() {
        assertEquals((1.5f * WIDTH) to (1.5f * WIDTH + 20f), span(columns(), window(9 to 30, 9 to 54)).pair())
    }

    @Test
    fun aWindowNarrowerThanSixDpIsWidenedAboutItsCentre() {
        // 9:30 to 9:34 is 3.3 dp, centred at 77.2 dp... widened to 6 dp about that centre.
        val bar = assertNotNull(span(columns(), window(9 to 30, 9 to 34)))
        assertEquals(MIN_BAR_DP, bar.end - bar.start, 0.001f)
        assertEquals((1.5f * WIDTH + 1.6666f), (bar.start + bar.end) / 2, 0.01f)
    }

    @Test
    fun aNarrowWindowAtTheRowsEdgesIsKeptInsideIt() {
        val first = assertNotNull(span(columns(), window(8 to 0, 8 to 0)))
        assertEquals(0f to MIN_BAR_DP, first.start to first.end)
        val last = assertNotNull(span(columns(), window(13 to 0, 13 to 0)))
        assertEquals((5 * WIDTH - MIN_BAR_DP) to 5 * WIDTH, last.start to last.end)
    }

    @Test
    fun aNarrowWindowInTheMiddleIsCentredOnItsPlace() {
        val bar = assertNotNull(span(columns(), window(10 to 0, 10 to 0)))
        assertEquals(2 * WIDTH, (bar.start + bar.end) / 2, 0.001f)
    }

    @Test
    fun theBarTakesItsEndsFromTheWindowSoTheTimesAgree() {
        // Seconds in the engine's times: the window rounds the hang-out up and the bring-in down.
        val advice = advice(Verdict.WILL_DRY, at(day0, 9 to 15) + 20.seconds, at(day0, 10 to 45) + 40.seconds)
        val window = assertNotNull(advice.window())
        assertEquals(at(day0, 9 to 16), window.hangOut)
        assertEquals(at(day0, 10 to 45), window.bringIn)
        val bar = assertNotNull(span(columns(), window))
        assertEquals((1 + 16 / 60f) * WIDTH, bar.start, 0.001f)
        assertEquals(2.75f * WIDTH, bar.end, 0.001f)
    }

    @Test
    fun timesThatMeetInOneMinuteGiveAThinBarAtThatMinute() {
        val advice = advice(Verdict.WILL_DRY, at(day0, 10 to 0) + 10.seconds, at(day0, 10 to 0) + 40.seconds)
        val window = assertNotNull(advice.window())
        assertEquals(window.hangOut, window.bringIn)
        val bar = assertNotNull(span(columns(), window))
        assertEquals(MIN_BAR_DP, bar.end - bar.start, 0.001f)
    }

    @Test
    fun aHangOutBeforeTheFirstColumnAndABringInAfterTheLastAreClamped() {
        val synthetic = Window(at(day0, 6 to 0), at(day0, 15 to 0))
        assertEquals(0f to 5 * WIDTH, span(columns(), synthetic).pair())
    }

    @Test
    fun noWindowOrNoColumnsGiveNoBar() {
        assertNull(span(columns(), null))
        assertNull(span(emptyList(), window(9 to 0, 10 to 0)))
    }

    @Test
    fun barsFollowTheForecastsZoneOnTheDaysTheClocksChange() {
        for ((zone, date) in listOf(
            london to LocalDate(2025, 3, 30),
            london to LocalDate(2025, 10, 26),
            auckland to LocalDate(2025, 9, 28),
        )) {
            val f = forecast(zone = zone, first = date)
            val now = at(date, 5 to 0, zone)
            val advice = advise(f, Load.LIGHT, now).first()
            val cols = dayHours(f, date, advice, now, twentyFourHourHourFormat)
            val window = Window(at(date, 9 to 30, zone), at(date, 11 to 15, zone))
            val bar = assertNotNull(barSpan(cols, window, WIDTH, cols.size * WIDTH))
            val firstHour = cols.first().time.toInt()
            assertEquals((9 - firstHour + 0.5f) * WIDTH, bar.start, 0.001f, "$zone $date")
            assertEquals((11 - firstHour + 0.25f) * WIDTH, bar.end, 0.001f, "$zone $date")
        }
    }

    private fun windowOf(f: Forecast, now: Instant, index: Int = 0): Pair<List<HourColumn>, Window?> {
        val day = advise(f, Load.LIGHT, now)[index]
        val date = f.days[index].date
        return dayHours(f, date, day, now, twentyFourHourHourFormat) to day?.window()
    }

    @Test
    fun aDontBotherDayHasNoBar() {
        val f = forecast(weather = { _, _ -> rain })
        val (cols, window) = windowOf(f, at(day0, 5 to 0))
        assertNull(window)
        assertNull(span(cols, window))
    }

    @Test
    fun aNullDayPolarNightAndTodayAfterSunsetHaveNoBar() {
        val missing = forecast(skipDays = setOf(day0))
        val (nullCols, nullWindow) = windowOf(missing, at(day0, 8 to 0), 0)
        assertNull(span(nullCols, nullWindow))
        val night = forecast(polar = Polar.NIGHT)
        val (nightCols, nightWindow) = windowOf(night, at(day0, 8 to 0))
        assertNull(span(nightCols, nightWindow))
        val late = forecast()
        val (lateCols, lateWindow) = windowOf(late, at(day0, 22 to 0))
        assertNull(span(lateCols, lateWindow))
    }

    @Test
    fun todayAfterTheHangOutTimeStartsTheBarAtNowInTheFirstColumn() {
        val f = forecast()
        val now = at(day0, 10 to 30)
        val (cols, window) = windowOf(f, now)
        val bar = assertNotNull(span(cols, window))
        assertEquals(cols.first().start, at(day0, 10 to 0))
        assertEquals(0.5f * WIDTH, bar.start, 0.001f)
    }

    @Test
    fun theRowOpensOneColumnBeforeTheBarsFirstColumn() {
        assertEquals(1, openingColumn(columns(), window(10 to 15, 11 to 0)))
        assertEquals(0, openingColumn(columns(), window(9 to 15, 11 to 0)))
        assertEquals(0, openingColumn(columns(), window(8 to 0, 11 to 0)))
        assertEquals(3, openingColumn(columns(), window(12 to 59, 13 to 0)))
    }

    @Test
    fun aDayWithoutABarOpensAtItsFirstColumn() {
        assertEquals(0, openingColumn(columns(), null))
        assertEquals(0, openingColumn(emptyList(), window(9 to 0, 10 to 0)))
    }

    @Test
    fun theLabelIsOnePhraseOrTheSingleTime() {
        val message = barLabel(window(10 to 15, 11 to 0), london, twentyFourHourFormat)
        assertEquals(Res.string.forecast_bar_label, message.resource)
        assertEquals(listOf("10:15", "11:00"), message.args)
        val single = barLabel(window(10 to 15, 10 to 15), london, twentyFourHourFormat)
        assertEquals(Res.string.forecast_bar_single, single.resource)
        assertEquals(listOf("10:15"), single.args)
    }

    @Test
    fun theDrynessLineNamesTheBringInInTheForecastsZone() {
        val partly = advice(Verdict.PARTLY_DRY, at(day0, 9 to 0), at(day0, 14 to 30) + 30.seconds, dryness = 70)
        val line = assertNotNull(partly.drynessLine(kathmandu, twentyFourHourFormat))
        assertEquals(Res.string.forecast_dryness, line.resource)
        // 14:30 in London in June is 13:30 UTC, 19:15 in Kathmandu.
        assertEquals(listOf("70 %", "19:15"), line.args)
    }

    @Test
    fun theDrynessLineIsOnlyForAPartlyDryDayWithTimes() {
        assertNull(
            advice(Verdict.WILL_DRY, at(day0, 9 to 0), at(day0, 14 to 0)).drynessLine(london, twentyFourHourFormat),
        )
        assertNull(advice(Verdict.DONT_BOTHER, null, null, dryness = 0).drynessLine(london, twentyFourHourFormat))
    }

    @Test
    fun theZoneNoteShowsOnADontBotherDayWithARow() {
        val now = at(day0, 5 to 0)
        assertTrue(showsZoneNote(columns(), null, london, auckland, now))
        assertFalse(showsZoneNote(columns(), null, london, london, now))
        assertFalse(showsZoneNote(emptyList(), null, london, auckland, now))
        assertTrue(showsZoneNote(emptyList(), window(9 to 0, 10 to 0), london, auckland, now))
    }

    @Test
    fun everyDayWithAWindowHasAnHoursRow() {
        var windows = 0
        fun check(label: String, f: Forecast, now: Instant) {
            for (load in Load.entries) {
                advise(f, load, now).forEachIndexed { index, day ->
                    if (day?.window() == null) return@forEachIndexed
                    windows++
                    val date = now.toLocalDateTime(f.timeZone).date.plus(index, DateTimeUnit.DAY)
                    val cols = dayHours(f, date, day, now, twentyFourHourHourFormat)
                    assertTrue(cols.isNotEmpty(), "$label $load day $index")
                    assertNotNull(span(cols, day.window()), "$label $load day $index")
                }
            }
        }
        for (case in allReferenceCases) check(case.id, case.forecast, case.now)
        val synthetic = listOf(
            "default" to forecast(),
            "grey" to forecast(weather = { _, _ -> grey }),
            "half-hour daylight" to forecast(sunrise = 6 to 30, sunset = 20 to 30),
            "polar day" to forecast(polar = Polar.DAY),
            "auckland" to forecast(zone = auckland),
            "rain from noon" to forecast(weather = { _, h -> if (h >= 12) rain else io.github.dryingday.engine.sunny }),
        )
        for ((label, f) in synthetic) {
            for (hour in listOf(0, 5, 8, 11, 14, 19)) check("$label at $hour", f, at(day0, hour to 30, f.timeZone))
        }
        assertTrue(windows > 20, "only $windows days with a window were checked")
    }
}
