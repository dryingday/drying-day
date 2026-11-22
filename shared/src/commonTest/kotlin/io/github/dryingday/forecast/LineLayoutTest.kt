package io.github.dryingday.forecast

import kotlin.test.Test
import kotlin.test.assertEquals

private const val LINE = 360f
private const val SMALL = 62f
private const val GAP = 8f

class LineLayoutTest {
    @Test
    fun theOpenClothTakesWhatTheTwoSmallOnesAndTheGapsLeave() {
        assertEquals(LINE - 2 * SMALL - 2 * GAP, openClothWidth(LINE, SMALL, GAP))
    }

    @Test
    fun theOpenClothIsNeverNarrowerThanASmallOne() {
        assertEquals(SMALL, openClothWidth(100f, SMALL, GAP))
    }

    @Test
    fun aClothRunsFromSmallToOpenAsItOpens() {
        val open = openClothWidth(LINE, SMALL, GAP)

        assertEquals(62, clothWidth(0f, SMALL, open))
        assertEquals(open.toInt(), clothWidth(1f, SMALL, open))
        assertEquals(((SMALL + open) / 2).toInt(), clothWidth(0.5f, SMALL, open))
        assertEquals(62, clothWidth(-0.2f, SMALL, open))
        assertEquals(open.toInt(), clothWidth(1.3f, SMALL, open))
    }

    // One closing as another opens: their openness adds up to 1, so the line stays full.
    @Test
    fun theThreeWidthsFillTheLineWhileOneOpensAndAnotherCloses() {
        val open = openClothWidth(LINE, SMALL, GAP)
        for (step in 0..10) {
            val opening = step / 10f
            val total = clothWidth(1f - opening, SMALL, open) + clothWidth(opening, SMALL, open) +
                clothWidth(0f, SMALL, open) + 2 * GAP
            assertEquals(LINE, total, 1f, "at $opening")
        }
    }

    @Test
    fun theSharedHeightIsTheTallestDay() {
        assertEquals(300, sharedClothHeight(listOf(180, 300, 240), 120))
    }

    @Test
    fun theSharedHeightIsAtLeastTheMinimum() {
        assertEquals(120, sharedClothHeight(listOf(80, 100, 90), 120))
        assertEquals(120, sharedClothHeight(emptyList(), 120))
    }
}
