package io.github.dryingday.reference

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** The generated reference set is complete enough for the engine to give all three days a verdict. */
class ReferenceForecastsTest {
    private val cases = referenceCases()

    @Test
    fun thereAreAboutThreeDozenCasesWithUniqueIds() {
        assertTrue(cases.size in 30..45, "${cases.size} cases")
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
    }

    @Test
    fun nowFallsOnTheCaseDateInItsZone() {
        for (case in cases) {
            assertEquals(case.date, case.now.toLocalDateTime(case.forecast.timeZone).date, case.id)
        }
    }

    @Test
    fun theCaseDayAndTheTwoAfterHaveDaylightData() {
        for (case in cases) {
            val dates = case.forecast.days.map { it.date }
            for (offset in 0..2) {
                assertTrue(case.date.plus(DatePeriod(days = offset)) in dates, "${case.id} +$offset: $dates")
            }
        }
    }

    @Test
    fun noHourIsMissingInThoseDaysDaylight() {
        for (case in cases) {
            val starts = case.forecast.hours.map { it.start }.toSet()
            val days = case.forecast.days.filter {
                it.date >= case.date &&
                    it.date <= case.date.plus(DatePeriod(days = 2))
            }
            for (day in days) {
                var hour = starts.filter { it <= day.sunrise }.maxOrNull()
                    ?: error("${case.id}: no hour at sunrise on ${day.date}")
                while (hour < day.sunset) {
                    assertTrue(hour in starts, "${case.id}: no hour from $hour")
                    hour += 1.hours
                }
            }
        }
    }
}
