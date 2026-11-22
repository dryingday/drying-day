package io.github.dryingday.forecast

import io.github.dryingday.engine.Verdict

// What each verdict's cloth does when it's chosen, as numbers. Nothing here draws.
// See docs/bolts/09-washing-line.md, "Motion".

/** A shake: leaning from the top edge up to [leanDegrees] each way, [swings] times, over [durationMs]. */
internal data class Shake(val leanDegrees: Float, val swings: Int, val durationMs: Int)

/**
 * What a cloth does. [shake] is null when animations are off. [spray] is the number of drops thrown on a shake.
 * The drips are how many a small and an open cloth hang; with animations off they hang still ([dripsFall] false).
 */
internal data class Motion(
    val shake: Shake?,
    val spray: Int,
    val dripsSmall: Int,
    val dripsOpen: Int,
    val dripsFall: Boolean,
)

private val DRY_SHAKE = Shake(leanDegrees = 4f, swings = 3, durationMs = 900)
private val PARTLY_SHAKE = Shake(leanDegrees = 6f, swings = 5, durationMs = 800)
private val WET_SHAKE = Shake(leanDegrees = 9f, swings = 6, durationMs = 750)

private const val PARTLY_SPRAY = 12
private const val WET_SPRAY = 26
private const val PARTLY_DRIPS_SMALL = 1
private const val PARTLY_DRIPS_OPEN = 3
private const val WET_DRIPS_SMALL = 3
private const val WET_DRIPS_OPEN = 8

/**
 * The motion for a cloth of [verdict] (null for a null day). A day the washing was never out ([neverOut]) moves
 * as a will-dry one, with no spray and no drips. With [animationsOn] false there's no shake or spray and the
 * drips hang still.
 */
internal fun motion(verdict: Verdict?, neverOut: Boolean, animationsOn: Boolean): Motion {
    val effective = if (neverOut) null else verdict
    val still = when (effective) {
        null, Verdict.WILL_DRY -> Motion(DRY_SHAKE, 0, 0, 0, dripsFall = true)
        Verdict.PARTLY_DRY -> Motion(PARTLY_SHAKE, PARTLY_SPRAY, PARTLY_DRIPS_SMALL, PARTLY_DRIPS_OPEN, true)
        Verdict.DONT_BOTHER -> Motion(WET_SHAKE, WET_SPRAY, WET_DRIPS_SMALL, WET_DRIPS_OPEN, true)
    }
    return if (animationsOn) still else still.copy(shake = null, spray = 0, dripsFall = false)
}
