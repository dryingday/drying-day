package io.github.dryingday.forecast

// When the cloths hang one under another. See docs/bolts/09-washing-line.md, "Stacked".

/** At large font sizes the cloths side by side break words, so they stack. */
internal const val STACK_TABS_FROM_FONT_SCALE = 1.5f

/**
 * Whether the cloths stack: from [STACK_TABS_FROM_FONT_SCALE] up, or when the open cloth, [openClothWidthDp] wide,
 * can't hold [longestVerdictWordWidthDp], the measured width of the longest word of any verdict.
 */
internal fun stackCloths(fontScale: Float, openClothWidthDp: Float, longestVerdictWordWidthDp: Float): Boolean =
    fontScale >= STACK_TABS_FROM_FONT_SCALE || longestVerdictWordWidthDp > openClothWidthDp
