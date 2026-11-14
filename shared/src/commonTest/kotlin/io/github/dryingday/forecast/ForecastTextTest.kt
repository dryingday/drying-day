package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.IndoorStep
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Tip
import io.github.dryingday.engine.Verdict
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.day_today
import io.github.dryingday.shared.resources.day_tomorrow
import io.github.dryingday.shared.resources.verdict_day_over
import io.github.dryingday.shared.resources.verdict_dont_bother
import io.github.dryingday.shared.resources.verdict_partly_dry
import io.github.dryingday.shared.resources.verdict_short_none
import io.github.dryingday.shared.resources.verdict_will_dry
import io.github.dryingday.weather.SourceError
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val kathmandu = TimeZone.of("Asia/Kathmandu") // UTC+05:45
private val london = TimeZone.of("Europe/London")
private val utc = TimeZone.of("Africa/Abidjan") // UTC all year
private val date = LocalDate(2025, 6, 14) // A Saturday.

@Suppress("LongParameterList") // A test builder: every parameter after the verdict has a default.
private fun advice(
    verdict: Verdict,
    hangOut: String? = null,
    bringIn: String? = null,
    dryness: Int = 100,
    reasons: List<Reason> = emptyList(),
    indoorPlan: List<IndoorStep> = emptyList(),
) = DayAdvice(
    date,
    verdict,
    hangOut?.let(Instant::parse),
    bringIn?.let(Instant::parse),
    dryness,
    reasons,
    emptyList<Tip>(),
    indoorPlan,
)

private fun <T> assertAllDistinct(codes: Collection<T>, text: (T) -> String) {
    assertEquals(codes.size, codes.map(text).toSet().size)
}

class ForecastTextTest {
    @Test
    fun everyCodeHasItsOwnString() {
        assertAllDistinct(Reason.entries) { it.text().key }
        assertAllDistinct(Tip.entries) { it.text().key }
        assertAllDistinct(IndoorStep.entries) { it.text().key }
        assertAllDistinct(SourceError.entries) { it.textWithoutForecast().key }
        assertAllDistinct(SourceError.entries) { it.textUnderForecast().key }
        assertAllDistinct(DayOfWeek.entries) { it.text().key }
        assertTrue(SourceError.entries.all { it.textWithoutForecast().key != it.textUnderForecast().key })
    }

    @Test
    fun headlinesFollowTheVerdict() {
        assertEquals(
            Res.string.verdict_will_dry,
            advice(Verdict.WILL_DRY, "2025-06-14T09:00:00Z", "2025-06-14T15:00:00Z").headline(),
        )
        assertEquals(
            Res.string.verdict_partly_dry,
            advice(
                Verdict.PARTLY_DRY,
                "2025-06-14T09:00:00Z",
                "2025-06-14T15:00:00Z",
                70,
                listOf(Reason.RAIN),
            ).headline(),
        )
        assertEquals(
            Res.string.verdict_dont_bother,
            advice(Verdict.DONT_BOTHER, dryness = 20, reasons = listOf(Reason.RAIN)).headline(),
        )
    }

    @Test
    fun aDayWhoseOnlyReasonIsDayOverSaysItIsTooLate() {
        val over = advice(
            Verdict.DONT_BOTHER,
            dryness = 0,
            reasons = listOf(Reason.DAY_OVER),
            indoorPlan = listOf(IndoorStep.AIRER_SPACED),
        )
        assertEquals(Res.string.verdict_day_over, over.headline())
        assertEquals(listOf(IndoorStep.AIRER_SPACED), over.indoorPlan)

        val andRain = advice(Verdict.DONT_BOTHER, dryness = 0, reasons = listOf(Reason.DAY_OVER, Reason.RAIN))
        assertEquals(Res.string.verdict_dont_bother, andRain.headline())
    }

    @Test
    fun theTabsLabelTodayTomorrowAndTheWeekdayEvenForANullDay() {
        assertEquals(Res.string.day_today, dayLabel(date, 0))
        assertEquals(Res.string.day_tomorrow, dayLabel(date, 1))
        assertEquals(DayOfWeek.MONDAY.text(), dayLabel(date, 2))
        // The 17th is a Tuesday, so two days on is a Thursday.
        assertEquals(DayOfWeek.THURSDAY.text(), dayLabel(LocalDate(2025, 6, 17), 2))
        assertEquals(Res.string.verdict_short_none, (null as DayAdvice?).shortVerdict())
    }

    @Test
    fun theHangOutIsRoundedUpAndTheBringInDownAtAQuarterHourOffset() {
        val day = advice(Verdict.WILL_DRY, "2025-06-14T10:14:30Z", "2025-06-14T14:30:59Z")

        val window = checkNotNull(day.window())

        assertEquals("16:00", twentyFourHourFormat(window.hangOut, kathmandu))
        assertEquals("20:15", twentyFourHourFormat(window.bringIn, kathmandu))
    }

    @Test
    fun aTimeOnTheMinuteIsLeftAlone() {
        val window = checkNotNull(advice(Verdict.WILL_DRY, "2025-06-14T10:15:00Z", "2025-06-14T14:30:00Z").window())

        assertEquals(Instant.parse("2025-06-14T10:15:00Z"), window.hangOut)
        assertEquals(Instant.parse("2025-06-14T14:30:00Z"), window.bringIn)
    }

    @Test
    fun aWindowAcrossTheClocksGoingForwardShowsEachEndOnItsOwnClock() {
        // 01:00 UTC on 2025-03-30 is when London skips from 01:00 to 02:00.
        val window = checkNotNull(advice(Verdict.WILL_DRY, "2025-03-30T00:29:30Z", "2025-03-30T01:30:59Z").window())

        assertEquals("00:30", twentyFourHourFormat(window.hangOut, london))
        assertEquals("02:30", twentyFourHourFormat(window.bringIn, london))
    }

    @Test
    fun twoTimesInOneMinuteBothShowTheHangOutsMinuteRoundedDown() {
        val window =
            checkNotNull(
                advice(
                    Verdict.PARTLY_DRY,
                    "2025-06-14T10:15:10Z",
                    "2025-06-14T10:15:50Z",
                    70,
                    listOf(Reason.RAIN),
                ).window(),
            )

        assertEquals(Instant.parse("2025-06-14T10:15:00Z"), window.hangOut)
        assertEquals(Instant.parse("2025-06-14T10:15:00Z"), window.bringIn)
    }

    @Test
    fun aDayWithNoTimesHasNoWindow() {
        assertNull(advice(Verdict.DONT_BOTHER, dryness = 10, reasons = listOf(Reason.RAIN)).window())
    }

    @Test
    fun theTwentyFourHourFormatPadsAndUsesTheGivenZone() {
        assertEquals("09:05", twentyFourHourFormat(Instant.parse("2025-06-14T08:05:00Z"), london))
        assertEquals("13:50", twentyFourHourFormat(Instant.parse("2025-06-14T08:05:00Z"), kathmandu))
    }

    @Test
    fun timesAreLocalOnlyWhenTheOffsetsDiffer() {
        val summer = Instant.parse("2025-06-14T12:00:00Z")
        val winter = Instant.parse("2025-12-14T12:00:00Z")

        assertFalse(timesAreLocalToPlace(london, london, summer))
        assertTrue(timesAreLocalToPlace(london, kathmandu, summer))
        // Different offsets in summer: BST against UTC.
        assertTrue(timesAreLocalToPlace(london, utc, summer))
        // The same offset in winter, in a different zone, doesn't count.
        assertFalse(timesAreLocalToPlace(london, utc, winter))
    }

    @Test
    fun updatedSaysTodayYesterdayOrTheWeekdayInThePlacesZone() {
        val now = Instant.parse("2025-06-14T09:00:00Z") // A Saturday.

        val today = updatedLabel(Instant.parse("2025-06-14T08:40:00Z"), now, utc, twentyFourHourFormat)
        assertEquals(UpdatedDay.TODAY, today.day)
        assertEquals("08:40", today.time)

        val yesterday = updatedLabel(Instant.parse("2025-06-13T21:10:00Z"), now, utc, twentyFourHourFormat)
        assertEquals(UpdatedDay.YESTERDAY, yesterday.day)
        assertEquals("21:10", yesterday.time)

        val older = updatedLabel(Instant.parse("2025-06-11T21:10:00Z"), now, utc, twentyFourHourFormat)
        assertEquals(UpdatedDay.WEEKDAY, older.day)
        assertEquals(DayOfWeek.WEDNESDAY, older.weekday)
    }

    @Test
    fun theDayIsCountedInThePlacesZoneNotUtc() {
        // 23:30 in Kathmandu (UTC+05:45) on the 14th is 17:45 UTC; now is 00:30 there on the 15th.
        val fetched = Instant.parse("2025-06-14T17:45:00Z")
        val now = Instant.parse("2025-06-14T18:45:00Z")

        val label = updatedLabel(fetched, now, kathmandu, twentyFourHourFormat)

        assertEquals(UpdatedDay.YESTERDAY, label.day)
        assertEquals("23:30", label.time)
    }

    @Test
    fun updatedNamesTheZoneAsAnOffset() {
        val now = Instant.parse("2025-06-14T12:00:00Z")

        assertEquals("UTC", updatedLabel(now, now, utc, twentyFourHourFormat).zone)
        assertEquals("UTC+1", updatedLabel(now, now, london, twentyFourHourFormat).zone)
        assertEquals("UTC+5:45", updatedLabel(now, now, kathmandu, twentyFourHourFormat).zone)
        assertEquals("UTC-4", updatedLabel(now, now, TimeZone.of("America/New_York"), twentyFourHourFormat).zone)
    }

    @Test
    fun updatedWithAFetchTimeAfterNowHasNoDay() {
        val now = Instant.parse("2025-06-14T23:50:00Z")

        val label = updatedLabel(Instant.parse("2025-06-15T00:10:00Z"), now, utc, twentyFourHourFormat)

        assertEquals(UpdatedDay.UNKNOWN, label.day)
        assertEquals("00:10", label.time)
    }

    @Test
    fun theDrynessLineHasThePercent() {
        assertEquals(
            "70 %",
            advice(
                Verdict.PARTLY_DRY,
                "2025-06-14T09:00:00Z",
                "2025-06-14T15:00:00Z",
                70,
                listOf(Reason.RAIN),
            ).drynessText(),
        )
    }
}
