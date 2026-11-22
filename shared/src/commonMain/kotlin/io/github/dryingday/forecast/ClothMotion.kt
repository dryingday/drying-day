package io.github.dryingday.forecast

import io.github.dryingday.engine.Verdict
import kotlin.math.floor
import kotlin.random.Random

// How a cloth moves, frame by frame: the shake's lean, the drips and the spray. Pure numbers, so the curves are
// tested without drawing; the counts come from [motion]. See docs/bolts/09-washing-line.md, "Motion".

/**
 * The lean in degrees [progress] (0 to 1) of the way through [shake]. The swings alternate in direction, die
 * away evenly from the largest lean, and are evenly spaced, with each stretch between two of them eased; it's 0
 * at the start and the end. Positive leans the hem to the right.
 */
internal fun shakeAngle(shake: Shake, progress: Float): Float {
    if (progress <= 0f || progress >= 1f || shake.swings <= 0) return 0f
    val position = progress * (shake.swings + 1)
    val stretch = floor(position).toInt()
    val from = swingPeak(shake, stretch - 1)
    val to = swingPeak(shake, stretch)
    return from + (to - from) * smoothStep(position - stretch)
}

private fun swingPeak(shake: Shake, index: Int): Float {
    if (index < 0 || index >= shake.swings) return 0f
    val size = shake.leanDegrees * (1f - index.toFloat() / shake.swings)
    return if (index % 2 == 0) size else -size
}

// The smooth step, 3t² − 2t³: no speed at either end of a stretch, like a swing turning.
@Suppress("MagicNumber")
private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

/** A drip: where it hangs, as a share of the cloth's width, and its own loop. */
internal class Drip(val at: Float, val periodMs: Int, val delayMs: Int)

// The mock's drips, small cloth's first. Loops of 1.8 to 3.6 s, started apart, so they don't fall together.
@Suppress("MagicNumber")
private val PARTLY_DRIPS = listOf(Drip(0.34f, 3_200, 0), Drip(0.58f, 3_000, 1_200), Drip(0.82f, 3_600, 2_100))

@Suppress("MagicNumber")
private val WET_DRIPS = listOf(
    Drip(0.18f, 2_000, 0),
    Drip(0.50f, 2_300, 900),
    Drip(0.82f, 2_100, 1_500),
    Drip(0.32f, 1_800, 400),
    Drip(0.64f, 2_200, 1_200),
    Drip(0.42f, 2_600, 1_900),
    Drip(0.92f, 1_900, 700),
    Drip(0.08f, 2_400, 1_300),
)

/** The first [count] drips for a cloth of [verdict]: a don't-bother cloth's are quicker. */
internal fun drips(verdict: Verdict?, count: Int): List<Drip> =
    (if (verdict == Verdict.DONT_BOTHER) WET_DRIPS else PARTLY_DRIPS).take(count)

/** A drop at one moment: [y] dp down from where it hangs, scaled about its top centre, and its [alpha]. */
internal data class DropFrame(val y: Float, val scaleX: Float, val scaleY: Float, val alpha: Float)

/** A drop hanging still at the hem. */
internal val STILL_DROP = DropFrame(0f, 1f, 1f, 1f)

/** How far a drip falls, in dp. */
internal const val DRIP_FALL_DP = 74f

private const val SWELL_END = 0.45f
private const val LET_GO = 0.55f
private const val START_SCALE = 0.15f
private const val LET_GO_DROP_DP = 2f
private const val LET_GO_STRETCH = 1.15f
private const val END_SCALE_X = 0.9f
private const val END_SCALE_Y = 1.2f

/**
 * Where [drip] is at [timeMs]: it swells at the hem, lets go, falls [DRIP_FALL_DP] and fades, then starts again.
 * Before its first loop starts it hangs still, and so it does when [falling] is false.
 */
internal fun dripFrame(drip: Drip, timeMs: Long, falling: Boolean): DropFrame {
    val since = timeMs - drip.delayMs
    if (!falling || since < 0) return STILL_DROP
    val t = (since % drip.periodMs).toFloat() / drip.periodMs
    val alpha = if (t <= SWELL_END) 1f else 1f - easeIn((t - SWELL_END) / (1f - SWELL_END))
    return when {
        t <= SWELL_END -> {
            val scale = lerp(START_SCALE, 1f, easeIn(t / SWELL_END))
            DropFrame(0f, scale, scale, alpha)
        }

        t <= LET_GO -> {
            val k = easeIn((t - SWELL_END) / (LET_GO - SWELL_END))
            DropFrame(LET_GO_DROP_DP * k, 1f, lerp(1f, LET_GO_STRETCH, k), alpha)
        }

        else -> {
            val k = easeIn((t - LET_GO) / (1f - LET_GO))
            DropFrame(
                lerp(LET_GO_DROP_DP, DRIP_FALL_DP, k),
                lerp(1f, END_SCALE_X, k),
                lerp(LET_GO_STRETCH, END_SCALE_Y, k),
                alpha,
            )
        }
    }
}

/** When the spray leaves, after the shake starts. */
internal const val SPRAY_START_MS = 120

/**
 * A drop thrown off a cloth's side: from the left ([edge] -1) or right (1) edge, [x] and [y] as shares of the
 * cloth's width and height, flying [dx] dp outward, rising [up] dp, then falling to [fall] dp below where it left
 * and turning [rotation] degrees, over [durationMs] after its [delayMs].
 */
@Suppress("LongParameterList") // One named number each, as the mock has them.
internal class SprayDrop(
    val edge: Int,
    val x: Float,
    val y: Float,
    val dx: Float,
    val up: Float,
    val fall: Float,
    val rotation: Float,
    val durationMs: Int,
    val delayMs: Int,
)

// The mock's ranges: in from the edge by up to 6 %, 25 % to 95 % down, 24 to 80 dp out, 8 to 34 dp up, 50 to
// 130 dp down, 30° to 90° of turn, 0.6 to 1 s each, up to 0.2 s late.
private const val EDGE_BAND = 0.06f
private const val TOP_SHARE = 0.25f
private const val HEIGHT_SHARE = 0.7f
private const val DX_LEAST = 24f
private const val DX_MORE = 56f
private const val UP_LEAST = 8f
private const val UP_MORE = 26f
private const val FALL_LEAST = 50f
private const val FALL_MORE = 80f
private const val TURN_LEAST = 30f
private const val TURN_MORE = 60f
private const val DURATION_LEAST_MS = 600
private const val DURATION_MORE_MS = 400
private const val DELAY_MORE_MS = 200

/** [count] drops, alternating sides, from [random]. */
internal fun sprayDrops(count: Int, random: Random): List<SprayDrop> = List(count) { index ->
    val edge = if (index % 2 == 0) -1 else 1
    val inset = random.nextFloat() * EDGE_BAND
    SprayDrop(
        edge = edge,
        x = if (edge > 0) 1f - inset else inset,
        y = TOP_SHARE + random.nextFloat() * HEIGHT_SHARE,
        dx = edge * (DX_LEAST + random.nextFloat() * DX_MORE),
        up = -(UP_LEAST + random.nextFloat() * UP_MORE),
        fall = FALL_LEAST + random.nextFloat() * FALL_MORE,
        rotation = edge * (TURN_LEAST + random.nextFloat() * TURN_MORE),
        durationMs = DURATION_LEAST_MS + (random.nextFloat() * DURATION_MORE_MS).toInt(),
        delayMs = (random.nextFloat() * DELAY_MORE_MS).toInt(),
    )
}

/** A spray drop at one moment: its offset in dp from where it left, its turn, scale and alpha. */
internal data class SprayFrame(val dx: Float, val dy: Float, val rotation: Float, val scale: Float, val alpha: Float)

private const val ARC_TOP = 0.4f
private const val ARC_OUT = 0.55f
private const val HALF_TURN = 0.5f
private const val THROWN_SCALE = 0.5f
private const val LANDING_SCALE = 0.9f

/** The last moment any spray drop is still in the air, after the shake starts. */
internal fun sprayEndMs(drops: List<SprayDrop>): Int =
    SPRAY_START_MS + (drops.maxOfOrNull { it.delayMs + it.durationMs } ?: 0)

/** Where [drop] is [elapsedMs] after the shake started; null before the spray leaves and once the drop has gone. */
internal fun sprayFrame(drop: SprayDrop, elapsedMs: Long): SprayFrame? {
    val t = (elapsedMs - SPRAY_START_MS - drop.delayMs).toFloat() / drop.durationMs
    return when {
        elapsedMs < SPRAY_START_MS || t > 1f -> null

        t <= 0f -> SprayFrame(0f, 0f, 0f, THROWN_SCALE, 1f)

        t <= ARC_TOP -> {
            val k = easeOut(t / ARC_TOP)
            SprayFrame(drop.dx * ARC_OUT * k, drop.up * k, drop.rotation * HALF_TURN * k, lerp(THROWN_SCALE, 1f, k), 1f)
        }

        else -> {
            val k = easeOut((t - ARC_TOP) / (1f - ARC_TOP))
            SprayFrame(
                lerp(drop.dx * ARC_OUT, drop.dx, k),
                lerp(drop.up, drop.fall, k),
                lerp(drop.rotation * HALF_TURN, drop.rotation, k),
                lerp(1f, LANDING_SCALE, k),
                1f - k,
            )
        }
    }
}

private fun lerp(from: Float, to: Float, k: Float): Float = from + (to - from) * k

private fun easeIn(t: Float): Float = t * t

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)
