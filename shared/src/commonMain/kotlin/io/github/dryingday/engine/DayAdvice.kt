package io.github.dryingday.engine

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** What's on the line. A mixed load is [HEAVY]: it's done when the slowest item is. */
enum class Load {
    /** Shirts, underwear, thin cotton, synthetics. */
    LIGHT,

    /** Towels, jeans, bedding, hoodies. */
    HEAVY,
}

enum class Verdict { WILL_DRY, PARTLY_DRY, DONT_BOTHER }

/** What held drying back, for [Verdict.PARTLY_DRY] and [Verdict.DONT_BOTHER]. Listed in this order. */
enum class Reason { DAY_OVER, RAIN, FOG, COLD, DAMP_AIR, STILL_AIR, SHORT_DAYLIGHT }

/** Hanging tips, in priority order. */
enum class Tip { PEG_WELL, SHOWERS_POSSIBLE, HEAVY_ITEMS, SPACE_OUT, DARKS_INSIDE_OUT }

/** Steps for finishing or doing the load indoors. */
enum class IndoorStep { AIRER_SPACED, OPEN_WINDOW, DEHUMIDIFIER_OR_CLOSED_ROOM, NOT_ON_RADIATOR, TURN_HEAVY_ITEMS }

/**
 * The engine's advice for one local [date]. Codes only: the UI turns them into text.
 *
 * @property dryness how dry the load gets in the chosen window, 0 to 100, rounded down: 100 for
 *   [Verdict.WILL_DRY], 50 to 99 for [Verdict.PARTLY_DRY], under 50 for [Verdict.DONT_BOTHER].
 * @property reasons and [indoorPlan] are empty for [Verdict.WILL_DRY]. These and [tips] list each code at
 *   most once, in the order of its enum.
 * @property hangOut and [bringIn] are null for [Verdict.DONT_BOTHER] and set, in order, otherwise.
 */
data class DayAdvice(
    val date: LocalDate,
    val verdict: Verdict,
    val hangOut: Instant?,
    val bringIn: Instant?,
    val dryness: Int,
    val reasons: List<Reason>,
    val tips: List<Tip>,
    val indoorPlan: List<IndoorStep>,
) {
    init {
        require(dryness in 0..MAX_DRYNESS) { "dryness must be 0 to 100, was $dryness" }
        val expected = when {
            dryness == MAX_DRYNESS -> Verdict.WILL_DRY
            dryness >= DryingThresholds.PARTLY_DRY_FROM_PERCENT -> Verdict.PARTLY_DRY
            else -> Verdict.DONT_BOTHER
        }
        require(verdict == expected) { "dryness $dryness means $expected, not $verdict" }
        if (verdict == Verdict.DONT_BOTHER) {
            require(hangOut == null && bringIn == null) { "DONT_BOTHER shows no times" }
        } else {
            require(hangOut != null && bringIn != null && hangOut < bringIn) {
                "$verdict needs a hang-out before its bring-in"
            }
        }
        if (verdict == Verdict.WILL_DRY) {
            require(reasons.isEmpty() && indoorPlan.isEmpty()) { "WILL_DRY has no reasons or indoor plan" }
        }
        require(tips.size <= MAX_TIPS) { "at most $MAX_TIPS tips, was ${tips.size}" }
        requireInEnumOrder(reasons, "reasons")
        requireInEnumOrder(tips, "tips")
        requireInEnumOrder(indoorPlan, "indoor plan")
    }

    private companion object {
        const val MAX_DRYNESS = 100
        const val MAX_TIPS = 3

        fun requireInEnumOrder(codes: List<Enum<*>>, name: String) =
            require(codes.zipWithNext().all { (a, b) -> a.ordinal < b.ordinal }) {
                "$name must be in order, each once: $codes"
            }
    }
}
