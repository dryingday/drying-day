package io.github.dryingday.engine

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DayAdviceTest {
    private val hangOut = Instant.parse("2025-06-14T08:00:00Z")
    private val bringIn = Instant.parse("2025-06-14T12:30:00Z")

    private fun advice(
        verdict: Verdict = Verdict.WILL_DRY,
        hangOut: Instant? = this.hangOut,
        bringIn: Instant? = this.bringIn,
        dryness: Int = 100,
        tips: List<Tip> = emptyList(),
        reasons: List<Reason> = emptyList(),
        indoorPlan: List<IndoorStep> = emptyList(),
    ) = DayAdvice(
        date = LocalDate(2025, 6, 14),
        verdict = verdict,
        hangOut = hangOut,
        bringIn = bringIn,
        dryness = dryness,
        reasons = reasons,
        tips = tips,
        indoorPlan = indoorPlan,
    )

    @Test
    fun acceptsEachVerdictInItsShape() {
        advice()
        advice(Verdict.PARTLY_DRY, dryness = 60)
        advice(Verdict.DONT_BOTHER, hangOut = null, bringIn = null, dryness = 30)
    }

    @Test
    fun willDryMeansFullyDryAndOnlyThat() {
        assertFailsWith<IllegalArgumentException> { advice(Verdict.WILL_DRY, dryness = 99) }
        assertFailsWith<IllegalArgumentException> { advice(Verdict.PARTLY_DRY, dryness = 100) }
    }

    @Test
    fun halfDryIsTheLineBetweenPartlyAndDontBother() {
        advice(Verdict.PARTLY_DRY, dryness = 50)
        advice(Verdict.DONT_BOTHER, hangOut = null, bringIn = null, dryness = 49)
        assertFailsWith<IllegalArgumentException> { advice(Verdict.PARTLY_DRY, dryness = 49) }
        assertFailsWith<IllegalArgumentException> {
            advice(Verdict.DONT_BOTHER, hangOut = null, bringIn = null, dryness = 50)
        }
    }

    @Test
    fun willDryHasNoReasonsOrIndoorPlan() {
        assertFailsWith<IllegalArgumentException> { advice(reasons = listOf(Reason.RAIN)) }
        assertFailsWith<IllegalArgumentException> { advice(indoorPlan = listOf(IndoorStep.AIRER_SPACED)) }
        advice(
            Verdict.PARTLY_DRY,
            dryness = 70,
            reasons = listOf(Reason.RAIN),
            indoorPlan = listOf(IndoorStep.AIRER_SPACED),
        )
    }

    @Test
    fun codesAreInTheirOrderAndEachOnce() {
        assertFailsWith<IllegalArgumentException> { advice(tips = listOf(Tip.SPACE_OUT, Tip.PEG_WELL)) }
        assertFailsWith<IllegalArgumentException> { advice(tips = listOf(Tip.PEG_WELL, Tip.PEG_WELL)) }
        assertFailsWith<IllegalArgumentException> {
            advice(Verdict.PARTLY_DRY, dryness = 70, reasons = listOf(Reason.STILL_AIR, Reason.RAIN))
        }
        assertFailsWith<IllegalArgumentException> {
            advice(
                Verdict.PARTLY_DRY,
                dryness = 70,
                indoorPlan = listOf(IndoorStep.NOT_ON_RADIATOR, IndoorStep.AIRER_SPACED),
            )
        }
    }

    @Test
    fun drynessIsAPercentage() {
        assertFailsWith<IllegalArgumentException> { advice(Verdict.PARTLY_DRY, dryness = -1) }
        assertFailsWith<IllegalArgumentException> { advice(Verdict.WILL_DRY, dryness = 101) }
    }

    @Test
    fun dontBotherShowsNoTimes() {
        assertFailsWith<IllegalArgumentException> { advice(Verdict.DONT_BOTHER, dryness = 30) }
    }

    @Test
    fun otherVerdictsNeedBothTimesInOrder() {
        assertFailsWith<IllegalArgumentException> { advice(bringIn = null) }
        assertFailsWith<IllegalArgumentException> { advice(hangOut = bringIn, bringIn = hangOut) }
    }

    @Test
    fun atMostThreeTips() {
        assertFailsWith<IllegalArgumentException> {
            advice(tips = listOf(Tip.PEG_WELL, Tip.SHOWERS_POSSIBLE, Tip.HEAVY_ITEMS, Tip.SPACE_OUT))
        }
    }
}
