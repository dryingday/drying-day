package io.github.dryingday.forecast

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducedMotionTest {
    @Test
    fun removeAnimationsSetsTheScaleToZeroWhichIsOff() {
        assertFalse(animationsOnAtScale(0f))
    }

    @Test
    fun anyOtherScaleIsOn() {
        for (scale in listOf(0.5f, 1f, 1.5f, 2f, 5f, 10f)) assertTrue(animationsOnAtScale(scale), "$scale")
    }

    @Test
    fun reduceMotionIsOff() {
        assertFalse(animationsOnWithReduceMotion(true))
        assertTrue(animationsOnWithReduceMotion(false))
    }
}
