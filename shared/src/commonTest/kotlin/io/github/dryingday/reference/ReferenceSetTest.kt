package io.github.dryingday.reference

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
 * hang-out and bring-in within an hour. Disagreements left open are skipped; docs/reference-set.md lists them.
 */
class ReferenceSetTest {
    private val expectations = referenceExpectations()

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
        val cases = allReferenceCases.associateBy { it.id }
        val failures = expectations.filter { it.decision != Decision.OPEN }.mapNotNull { expected ->
            val case = cases.getValue(expected.id)
            val advice = advise(case.forecast, expected.load, case.now).first()
                ?: return@mapNotNull "${expected.id} ${expected.load}: no verdict"
            fun local(t: Instant?) = t?.toLocalDateTime(case.forecast.timeZone)?.let { it.hour * MINUTES + it.minute }
            fun minutes(t: String?) = t?.let { it.take(2).toInt() * MINUTES + it.takeLast(2).toInt() }
            fun near(actual: Instant?, label: String?) = (local(actual) == null && label == null) ||
                (local(actual) != null && label != null && abs(local(actual)!! - minutes(label)!!) <= MINUTES)
            val ok = advice.verdict == expected.verdict &&
                near(advice.hangOut, expected.hangOut) &&
                near(advice.bringIn, expected.bringIn)
            if (ok) {
                null
            } else {
                "${expected.id} ${expected.load}: engine ${advice.verdict} ${hm(local(advice.hangOut))}–" +
                    "${hm(local(advice.bringIn))}, " +
                    "expected ${expected.verdict} ${expected.hangOut}–${expected.bringIn} (${expected.decision})"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun hm(minutes: Int?) =
        minutes?.let { "${(it / MINUTES).toString().padStart(2, '0')}:${(it % MINUTES).toString().padStart(2, '0')}" }

    private companion object {
        const val MINUTES = 60
    }
}
