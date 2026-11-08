package io.github.dryingday.reference

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.advise
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The engine against the reference set (docs/bolts/02-drying-rules.md, "Reference set"): on each case's
 * first day, the same verdict as the label, or the maintainer's answer where the label was wrong, and
 * hang-out and bring-in within an hour. Disagreements left open must still disagree, so docs/reference-set.md
 * stays true; the two days after only need a verdict.
 */
class ReferenceSetTest {
    private val expectations = referenceExpectations()
    private val cases = allReferenceCases.associateBy { it.id }

    @Test
    fun everyCaseAndLoadHasOneExpectation() {
        val keys = expectations.map { it.id to it.load }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(allReferenceCases.size * 2, keys.size)
    }

    @Test
    fun theDecisionsAreAsRecorded() {
        val counts = expectations.groupingBy { it.decision }.eachCount()
        assertEquals(mapOf(Decision.AGREED to 55, Decision.LABEL_WRONG to 4, Decision.OPEN to 15), counts)
    }

    @Test
    fun theEngineMatchesEverySettledCase() {
        val failures = expectations.filter { it.decision != Decision.OPEN }.mapNotNull { expected ->
            val (case, advice) = today(expected) ?: return@mapNotNull "${expected.id} ${expected.load}: no verdict"
            if (matches(advice, expected, case)) null else mismatch(advice, expected, case)
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun everyOpenDisagreementStillDisagrees() {
        val nowAgreeing = expectations.filter { it.decision == Decision.OPEN }.mapNotNull { expected ->
            val (case, advice) = today(expected) ?: return@mapNotNull null
            if (matches(advice, expected, case)) "${expected.id} ${expected.load} now agrees: settle it" else null
        }
        assertTrue(nowAgreeing.isEmpty(), nowAgreeing.joinToString("\n"))
    }

    @Test
    fun theTwoDaysAfterGetAVerdict() {
        val missing = expectations.mapNotNull { expected ->
            val case = cases.getValue(expected.id)
            val days = advise(case.forecast, expected.load, case.now)
            if (days.drop(1).all { it != null }) null else "${expected.id} ${expected.load}: $days"
        }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }

    private fun today(expected: ReferenceExpectation): Pair<ReferenceCase, DayAdvice>? {
        val case = cases.getValue(expected.id)
        return advise(case.forecast, expected.load, case.now).first()?.let { case to it }
    }

    private fun matches(advice: DayAdvice, expected: ReferenceExpectation, case: ReferenceCase) =
        advice.verdict == expected.verdict &&
            withinAnHour(case.local(advice.hangOut), minutes(expected.hangOut)) &&
            withinAnHour(case.local(advice.bringIn), minutes(expected.bringIn))

    private fun mismatch(advice: DayAdvice, expected: ReferenceExpectation, case: ReferenceCase): String {
        val engine = listOf(advice.hangOut, advice.bringIn).map { hm(case.local(it)) }
        return "${expected.id} ${expected.load}: engine ${advice.verdict} ${engine.joinToString("–")}, " +
            "expected ${expected.verdict} ${expected.hangOut ?: "-"}–${expected.bringIn ?: "-"} (${expected.decision})"
    }

    private fun withinAnHour(actual: Int?, expected: Int?) = when {
        actual == null || expected == null -> actual == expected
        else -> abs(actual - expected) <= MINUTES
    }

    private fun ReferenceCase.local(t: Instant?) =
        t?.toLocalDateTime(forecast.timeZone)?.let { it.hour * MINUTES + it.minute }

    private fun minutes(hhmm: String?) = hhmm?.let { it.take(2).toInt() * MINUTES + it.takeLast(2).toInt() }

    private fun hm(minutes: Int?) =
        minutes?.let { "${(it / MINUTES).toString().padStart(2, '0')}:${(it % MINUTES).toString().padStart(2, '0')}" }
            ?: "-"

    private companion object {
        const val MINUTES = 60
    }
}
