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
 * @property dryness how dry the load gets in the chosen window, 0 to 100, rounded down; 100 only for
 *   [Verdict.WILL_DRY].
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
        require((verdict == Verdict.WILL_DRY) == (dryness == MAX_DRYNESS)) {
            "dryness 100 means WILL_DRY and only that; was $verdict at $dryness"
        }
        if (verdict == Verdict.DONT_BOTHER) {
            require(hangOut == null && bringIn == null) { "DONT_BOTHER shows no times" }
        } else {
            require(hangOut != null && bringIn != null && hangOut < bringIn) {
                "$verdict needs a hang-out before its bring-in"
            }
        }
        require(tips.size <= MAX_TIPS) { "at most $MAX_TIPS tips, was ${tips.size}" }
    }

    private companion object {
        const val MAX_DRYNESS = 100
        const val MAX_TIPS = 3
    }
}
