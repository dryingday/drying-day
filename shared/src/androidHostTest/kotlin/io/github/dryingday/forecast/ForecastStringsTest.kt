package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

// On the JVM host the resources reader needs Android's Log, so this reads the strings files from the module
// directory, as FontsTest does, and resolves a message by hand.
@Suppress("MagicNumber") // Test data: dryness percentages and a week's days.
class ForecastStringsTest {
    private val strings: Map<String, String> =
        File("src/commonMain/composeResources/values").listFiles { file -> file.extension == "xml" }.orEmpty()
            .flatMap { file ->
                Regex("""<string name="([^"]+)">(.*?)</string>""").findAll(file.readText())
                    .map { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
            }.toMap()

    private fun resolve(message: Message): String =
        message.args.foldIndexed(checkNotNull(strings[message.resource.key])) { index, text, arg ->
            text.replace("%${index + 1}\$s", arg)
        }

    private val saturday = LocalDate(2025, 6, 14)

    private fun advice(verdict: Verdict, dryness: Int, reasons: List<Reason>, times: Boolean) = DayAdvice(
        saturday,
        verdict,
        if (times) kotlin.time.Instant.parse("2025-06-14T09:00:00Z") else null,
        if (times) kotlin.time.Instant.parse("2025-06-14T15:00:00Z") else null,
        dryness,
        reasons,
        emptyList(),
        emptyList(),
    )

    @Test
    fun theVerdictsAreTheShortOnes() {
        assertEquals("Will dry", resolve(Message(advice(Verdict.WILL_DRY, 100, emptyList(), true).headline())))
        assertEquals(
            "Finish indoors",
            resolve(Message(advice(Verdict.PARTLY_DRY, 70, listOf(Reason.RAIN), true).headline())),
        )
        assertEquals(
            "Dry indoors",
            resolve(Message(advice(Verdict.DONT_BOTHER, 10, listOf(Reason.RAIN), false).headline())),
        )
        assertEquals(
            "Too late today",
            resolve(Message(advice(Verdict.DONT_BOTHER, 0, listOf(Reason.DAY_OVER), false).headline())),
        )
    }

    @Test
    fun theShortDayNamesAreThreeLetters() {
        val names = (0 until 7).map {
            resolve(Message(clothShortName(LocalDate.fromEpochDays(saturday.toEpochDays() + it - 2), 2)))
        }
        // The 14th is a Saturday; two days on from the two days before it is the Saturday itself, then round the week.
        assertEquals(listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"), names)
    }

    @Test
    fun theSpokenLabelsReadAsTheSpecSays() {
        val partly = advice(Verdict.PARTLY_DRY, 70, listOf(Reason.RAIN), true)
        val over = advice(Verdict.DONT_BOTHER, 0, listOf(Reason.DAY_OVER), false)

        assertEquals("Tomorrow, Finish indoors", clothSpoken(saturday, 1, partly, ::resolve))
        assertEquals("Today, Too late today", clothSpoken(saturday, 0, over, ::resolve))
        assertEquals("Tomorrow", clothSpoken(saturday, 1, null, ::resolve))
        assertEquals(
            "Monday, Dry indoors",
            clothSpoken(saturday, 2, advice(Verdict.DONT_BOTHER, 5, emptyList(), false), ::resolve),
        )
    }

    @Test
    fun theWindowNoteDescribesTheRowOfWashing() {
        assertEquals(
            "The washing runs from when it starts to dry until it's dry, or until damp air, rain or sunset would " +
                "start to undo the work. Hang it out at the start and bring it in by the end.",
            strings["window_note_open"],
        )
    }
}
