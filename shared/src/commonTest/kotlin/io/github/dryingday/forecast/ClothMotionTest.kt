package io.github.dryingday.forecast

import io.github.dryingday.engine.Verdict
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val SHAKES = listOf(Shake(4f, 3, 900), Shake(6f, 5, 800), Shake(9f, 6, 750))
private const val SAMPLES = 2_000

private fun samples(shake: Shake): List<Float> = (0..SAMPLES).map { shakeAngle(shake, it.toFloat() / SAMPLES) }

class ClothMotionTest {
    @Test
    fun aShakeStartsAndEndsUpright() {
        for (shake in SHAKES) {
            assertEquals(0f, shakeAngle(shake, 0f))
            assertEquals(0f, shakeAngle(shake, 1f))
            assertEquals(0f, shakeAngle(shake, -0.1f))
            assertEquals(0f, shakeAngle(shake, 1.1f))
        }
    }

    @Test
    fun aShakeLeansAsFarAsItsLargestLeanAndNoFurther() {
        for (shake in SHAKES) {
            val largest = samples(shake).maxOf { abs(it) }
            assertEquals(shake.leanDegrees, largest, 0.01f, "$shake")
        }
    }

    @Test
    fun aShakeSwingsItsNumberOfTimesEachWayInTurn() {
        for (shake in SHAKES) {
            val angles = samples(shake)
            // A swing is a turn at the end of a lean: where the angle stops growing and comes back.
            val turns = (1 until angles.lastIndex).count { i ->
                val here = abs(angles[i])
                here > abs(angles[i - 1]) && here >= abs(angles[i + 1]) && here > 0.01f
            }
            assertEquals(shake.swings, turns, "$shake")
            val signs = angles.filter { abs(it) > 0.01f }.map { it.sign }
            val changes = signs.zipWithNext().count { (a, b) -> a != b }
            assertEquals(shake.swings - 1, changes, "$shake")
        }
    }

    @Test
    fun eachSwingIsSmallerThanTheOneBefore() {
        for (shake in SHAKES) {
            val angles = samples(shake)
            val peaks = (1 until angles.lastIndex).filter { i ->
                abs(angles[i]) > abs(angles[i - 1]) && abs(angles[i]) >= abs(angles[i + 1])
            }.map { abs(angles[it]) }
            assertTrue(peaks.zipWithNext().all { (a, b) -> b < a }, "$shake: $peaks")
        }
    }

    @Test
    fun theDripsAreTheCountsMotionGives() {
        assertEquals(1, drips(Verdict.PARTLY_DRY, 1).size)
        assertEquals(3, drips(Verdict.PARTLY_DRY, 3).size)
        assertEquals(3, drips(Verdict.DONT_BOTHER, 3).size)
        assertEquals(8, drips(Verdict.DONT_BOTHER, 8).size)
        assertEquals(0, drips(Verdict.WILL_DRY, 0).size)
    }

    @Test
    fun theDripsLoopBetweenOnePointEightAndThreePointSixSecondsAndDontFallTogether() {
        for (verdict in listOf(Verdict.PARTLY_DRY, Verdict.DONT_BOTHER)) {
            val all = drips(verdict, 8)
            assertTrue(all.all { it.periodMs in 1_800..3_600 }, "$verdict")
            assertEquals(all.size, all.map { it.delayMs }.toSet().size, "$verdict")
            assertTrue(all.all { it.at in 0f..1f }, "$verdict")
        }
    }

    @Test
    fun aDripHangsStillWhenAnimationsAreOff() {
        val drip = drips(Verdict.DONT_BOTHER, 1).single()
        for (time in listOf(0L, 500L, 1_000L, 1_900L, 10_000L)) {
            assertEquals(STILL_DROP, dripFrame(drip, time, falling = false))
        }
    }

    @Test
    fun aDripSwellsAtTheHemThenFallsAndFades() {
        val drip = Drip(0.5f, 2_000, 0)

        val start = dripFrame(drip, 0, true)
        val swollen = dripFrame(drip, 900, true)
        val falling = dripFrame(drip, 1_600, true)
        val end = dripFrame(drip, 1_999, true)

        assertEquals(0f, start.y)
        assertTrue(start.scaleX < 0.2f)
        assertEquals(0f, swollen.y)
        assertEquals(1f, swollen.scaleX, 0.01f)
        assertTrue(falling.y > 2f && falling.y < DRIP_FALL_DP)
        assertTrue(falling.alpha in 0f..1f)
        assertEquals(DRIP_FALL_DP, end.y, 1f)
        assertTrue(end.alpha < 0.01f)
        // And it starts again.
        assertEquals(start, dripFrame(drip, 2_000, true))
    }

    @Test
    fun aDripWaitsForItsDelayHangingStill() {
        assertEquals(STILL_DROP, dripFrame(Drip(0.5f, 2_000, 1_200), 600, true))
    }

    @Test
    fun theSprayLeavesFromBothSidesAlongTheClothsHeight() {
        val drops = sprayDrops(26, Random(1))

        assertEquals(26, drops.size)
        assertEquals(13, drops.count { it.edge < 0 })
        for (drop in drops) {
            if (drop.edge <
                0
            ) {
                assertTrue(drop.x <= 0.06f && drop.dx < 0)
            } else {
                assertTrue(drop.x >= 0.94f && drop.dx > 0)
            }
            assertTrue(drop.y in 0.25f..0.95f)
            assertTrue(drop.up < 0f)
            assertTrue(drop.fall in 50f..130f)
            assertTrue(drop.durationMs in 600..1_000)
        }
    }

    @Test
    fun theSprayLeaves120MillisecondsInAndIsGoneWhenItsDropsLand() {
        val drops = sprayDrops(12, Random(2))
        val drop = drops.first()

        assertNull(sprayFrame(drop, SPRAY_START_MS - 1L))
        assertNotNull(sprayFrame(drop, SPRAY_START_MS.toLong()))
        assertNull(sprayFrame(drop, (SPRAY_START_MS + drop.delayMs + drop.durationMs + 1).toLong()))
        assertTrue(drops.all { sprayEndMs(drops) >= SPRAY_START_MS + it.delayMs + it.durationMs })
    }

    @Test
    fun aSprayDropArcsUpAndOutThenFallsAndFades() {
        val drop = SprayDrop(1, 1f, 0.5f, 60f, -20f, 100f, 60f, 800, 0)

        val top = checkNotNull(sprayFrame(drop, SPRAY_START_MS + 320L))
        val late = checkNotNull(sprayFrame(drop, SPRAY_START_MS + 800L))

        assertEquals(-20f, top.dy, 0.01f)
        assertEquals(33f, top.dx, 0.01f)
        assertEquals(1f, top.alpha)
        assertEquals(100f, late.dy, 0.01f)
        assertEquals(60f, late.dx, 0.01f)
        assertEquals(0f, late.alpha, 0.01f)
    }
}
