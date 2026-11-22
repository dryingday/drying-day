package io.github.dryingday.forecast

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StackingTest {
    @Test
    fun stackedFromFontScaleOnePointFive() {
        assertTrue(stackCloths(1.5f, 1000f, 10f))
        assertTrue(stackCloths(2f, 1000f, 10f))
    }

    @Test
    fun notStackedBelowItWhenTheWordFits() {
        assertFalse(stackCloths(1.49f, 200f, 120f))
        assertFalse(stackCloths(1f, 120f, 120f))
    }

    @Test
    fun stackedBelowItWhenTheLongestWordDoesntFit() {
        assertTrue(stackCloths(1.4f, 119f, 120f))
    }
}
