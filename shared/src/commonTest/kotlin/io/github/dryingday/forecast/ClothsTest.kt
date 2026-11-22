package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.cloth_spoken
import io.github.dryingday.shared.resources.day_short_friday
import io.github.dryingday.shared.resources.day_short_monday
import io.github.dryingday.shared.resources.day_short_saturday
import io.github.dryingday.shared.resources.day_short_sunday
import io.github.dryingday.shared.resources.day_short_thursday
import io.github.dryingday.shared.resources.day_short_tuesday
import io.github.dryingday.shared.resources.day_short_wednesday
import io.github.dryingday.shared.resources.day_today
import io.github.dryingday.shared.resources.day_tomorrow
import io.github.dryingday.shared.resources.verdict_day_over
import io.github.dryingday.shared.resources.verdict_partly_dry
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val saturday = LocalDate(2025, 6, 14)
private val monday = LocalDate(2025, 6, 16)

// Names a message by its key and arguments, so the structure is checked without resolving a string.
private fun keyed(message: Message) = message.resource.key + message.args

private fun advice(verdict: Verdict, dryness: Int, reasons: List<Reason>, times: Boolean) = DayAdvice(
    saturday,
    verdict,
    if (times) Instant.parse("2025-06-14T09:00:00Z") else null,
    if (times) Instant.parse("2025-06-14T15:00:00Z") else null,
    dryness,
    reasons,
    emptyList(),
    emptyList(),
)

class ClothsTest {
    @Test
    fun theShortNamesAreTodayThenTheWeekdayInThreeLetters() {
        assertEquals(Res.string.day_today, clothShortName(saturday, 0))
        assertEquals(Res.string.day_short_sunday, clothShortName(saturday, 1))
        assertEquals(Res.string.day_short_monday, clothShortName(saturday, 2))
    }

    @Test
    fun everyWeekdayHasItsOwnShortName() {
        // Two days on from the Saturday before a Monday is that Monday, and so on round the week.
        val names = (0 until DayOfWeek.entries.size).map {
            clothShortName(LocalDate.fromEpochDays(monday.toEpochDays() - 2 + it), 2).key
        }
        assertEquals(
            listOf(
                Res.string.day_short_monday,
                Res.string.day_short_tuesday,
                Res.string.day_short_wednesday,
                Res.string.day_short_thursday,
                Res.string.day_short_friday,
                Res.string.day_short_saturday,
                Res.string.day_short_sunday,
            ).map { it.key },
            names,
        )
    }

    @Test
    fun theFullNamesAreTodayTomorrowAndTheWeekday() {
        assertEquals(Res.string.day_today, dayLabel(saturday, 0))
        assertEquals(Res.string.day_tomorrow, dayLabel(saturday, 1))
        assertEquals(DayOfWeek.MONDAY.text(), dayLabel(saturday, 2))
    }

    @Test
    fun theSpokenLabelIsTheFullNameAndTheVerdict() {
        val partly = advice(Verdict.PARTLY_DRY, 70, listOf(Reason.RAIN), times = true)

        assertEquals(
            Res.string.cloth_spoken.key +
                listOf(keyed(Message(Res.string.day_tomorrow)), keyed(Message(Res.string.verdict_partly_dry))),
            clothSpoken(saturday, 1, partly, ::keyed),
        )
    }

    @Test
    fun aNullDaysLabelIsTheNameAlone() {
        assertEquals(keyed(Message(Res.string.day_tomorrow)), clothSpoken(saturday, 1, null, ::keyed))
    }

    @Test
    fun todayAfterSunsetReadsTooLateToday() {
        val over = advice(Verdict.DONT_BOTHER, 0, listOf(Reason.DAY_OVER), times = false)

        assertEquals(
            Res.string.cloth_spoken.key +
                listOf(keyed(Message(Res.string.day_today)), keyed(Message(Res.string.verdict_day_over))),
            clothSpoken(saturday, 0, over, ::keyed),
        )
    }
}
