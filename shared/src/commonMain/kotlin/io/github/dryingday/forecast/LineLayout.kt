package io.github.dryingday.forecast

import kotlin.math.roundToInt

// Where the cloths go on the line, as numbers: their widths while one opens, and the one height they share.
// The screen measures the text and draws the result. See docs/bolts/09-washing-line.md, "The line and the cloths".

/** A small cloth's width, in dp. */
internal const val SMALL_CLOTH_DP = 62f

/** The space between two cloths side by side, in dp. */
internal const val CLOTH_GAP_DP = 8f

/** The least height of a cloth, in dp. */
internal const val MIN_CLOTH_HEIGHT_DP = 120f

private const val CLOTHS = 3

/** The open cloth's width when three hang side by side on a line [lineWidth] wide: what the two small ones leave. */
internal fun openClothWidth(lineWidth: Float, small: Float, gap: Float): Float =
    maxOf(small, lineWidth - (CLOTHS - 1) * (small + gap))

/**
 * A cloth's width part of the way open: [openness] 0 is small, 1 is open. The three cloths' openness always adds
 * up to 1 while they move, so the three widths and the gaps always fill the line.
 */
internal fun clothWidth(openness: Float, small: Float, open: Float): Int =
    (small + (open - small) * openness.coerceIn(0f, 1f)).roundToInt()

/**
 * The one height of the three cloths: the tallest of the days' open content at the open cloth's final width, and at
 * least [minimum]. So the line doesn't change height when the day changes.
 */
internal fun sharedClothHeight(openHeights: List<Int>, minimum: Int): Int = maxOf(minimum, openHeights.maxOrNull() ?: 0)
