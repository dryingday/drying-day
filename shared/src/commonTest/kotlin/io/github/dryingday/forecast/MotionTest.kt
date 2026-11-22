package io.github.dryingday.forecast

import io.github.dryingday.engine.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val dry = Shake(leanDegrees = 4f, swings = 3, durationMs = 900)

private fun assertMotion(motion: Motion, shake: Shake, spray: Int, dripsSmall: Int, dripsOpen: Int) {
    assertEquals(shake, motion.shake)
    assertEquals(spray, motion.spray)
    assertEquals(dripsSmall, motion.dripsSmall)
    assertEquals(dripsOpen, motion.dripsOpen)
    assertTrue(motion.dripsFall)
}

class MotionTest {
    @Test
    fun willDryLeansLightlyAndThrowsNothing() {
        assertMotion(motion(Verdict.WILL_DRY, neverOut = false, animationsOn = true), dry, 0, 0, 0)
    }

    @Test
    fun partlyDryShakesHarderAndDripsOnceOrThrice() {
        assertMotion(motion(Verdict.PARTLY_DRY, false, true), Shake(6f, 5, 800), 12, 1, 3)
    }

    @Test
    fun dontBotherShakesHardestAndDripsMost() {
        assertMotion(motion(Verdict.DONT_BOTHER, false, true), Shake(9f, 6, 750), 26, 3, 8)
    }

    @Test
    fun aDayNeverOutMovesAsWillDryWhateverItsVerdict() {
        for (verdict in listOf(Verdict.WILL_DRY, Verdict.PARTLY_DRY, Verdict.DONT_BOTHER, null)) {
            assertMotion(motion(verdict, neverOut = true, animationsOn = true), dry, 0, 0, 0)
        }
    }

    @Test
    fun withAnimationsOffNothingMovesAndTheDripsHangStill() {
        for (verdict in Verdict.entries) {
            val still = motion(verdict, neverOut = false, animationsOn = false)
            assertNull(still.shake)
            assertEquals(0, still.spray)
            assertFalse(still.dripsFall)
        }
        val wet = motion(Verdict.DONT_BOTHER, neverOut = false, animationsOn = false)
        assertEquals(3, wet.dripsSmall)
        assertEquals(8, wet.dripsOpen)
        val never = motion(null, neverOut = true, animationsOn = false)
        assertNull(never.shake)
        assertEquals(0, never.dripsOpen)
        assertFalse(never.dripsFall)
    }
}
