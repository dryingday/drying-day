package io.github.dryingday.forecast

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.dryingday.WET_PATCH_DARKEST_ALPHA
import io.github.dryingday.WET_PATCH_LIGHTEST_ALPHA
import io.github.dryingday.WetPatchColor
import kotlin.math.PI
import kotlin.math.tan

// The cloths' drawing: the weave, the hard shadow, the pegs, the wet patch, the hem, the drips and the spray, and
// the skew a shake leans them by. All decorative. Sizes are the mock's (docs/bolts/09-washing-line.html).

internal val PEG_WIDTH = 8.dp
internal val PEG_HEIGHT = 19.dp

/** How far a cloth's pegs rise above its top edge, over the line. */
internal val PEG_RISE = 9.dp
private val PEG_INSET = 7.dp
private val PEG_CORNER = 2.dp
private val PEG_EDGE = 1.dp
private val PEG_SPLIT_LEFT = 3.dp
private val PEG_SPLIT_TOP = 7.dp
private val PEG_SPLIT_WIDTH = 1.dp
private val PEG_SPLIT_HEIGHT = 11.dp
private const val PEG_EDGE_ALPHA = 0.28f
private const val PEG_SPLIT_ALPHA = 0.45f

private val WEAVE_STEP = 3.dp
private val WEAVE_LINE = 1.dp
private const val WEAVE_ALPHA = 0.03f

private val SHADOW_X = 3.dp
private val SHADOW_Y = 4.dp
private const val SHADOW_ALPHA = 0.24f

private val HEM_FROM_BOTTOM = 9.dp
private val HEM_WIDTH = 2.dp
private val HEM_DASH = 6.dp
private val HEM_GAP = 4.dp
private const val HEM_ALPHA = 0.38f

private const val DEGREES_IN_HALF_TURN = 180.0

/** Draws [block] leant [degrees] about the top edge: the top stays on the line and the hem swings. */
internal fun DrawScope.skewedFromTop(degrees: Float, block: DrawScope.() -> Unit) {
    if (degrees == 0f) {
        block()
        return
    }
    val matrix = Matrix()
    matrix.values[Matrix.SkewX] = tan(degrees * PI / DEGREES_IN_HALF_TURN).toFloat()
    withTransform({ transform(matrix) }, block)
}

/** The hard shadow, 3 dp right and 4 dp down: under the cloth and its two pegs. */
internal fun DrawScope.clothShadow() {
    val shadow = Color.Black.copy(alpha = SHADOW_ALPHA)
    translate(SHADOW_X.toPx(), SHADOW_Y.toPx()) {
        drawRect(shadow)
        for (left in pegLefts()) {
            drawRoundRect(
                shadow,
                Offset(left, -PEG_RISE.toPx()),
                Size(PEG_WIDTH.toPx(), PEG_HEIGHT.toPx()),
                CornerRadius(PEG_CORNER.toPx()),
            )
        }
    }
}

/** The cloth colour with its faint weave: 1 dp lines every 3 dp both ways, black at 3 %. */
internal fun DrawScope.clothFace(cloth: Color) {
    drawRect(cloth)
    val weave = Color.Black.copy(alpha = WEAVE_ALPHA)
    val step = WEAVE_STEP.toPx()
    val line = WEAVE_LINE.toPx()
    var x = 0f
    while (x < size.width) {
        drawRect(weave, Offset(x, 0f), Size(line, size.height))
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawRect(weave, Offset(0f, y), Size(size.width, line))
        y += step
    }
}

// The patch's wavy top, from the mock: across the cloth as shares of its width, down from the patch's top in dp.
private val WAVE = listOf(
    0f to 5f,
    0.08f to 0f,
    0.17f to 4f,
    0.34f to 6f,
    0.52f to 3f,
    0.70f to 7f,
    0.86f to 4f,
    1f to 5f,
)
private const val WAVE_DP = 0.7f

/** The wet patch: [share] percent of the cloth's height up from the bottom edge, with a gently wavy top. */
internal fun DrawScope.wetPatch(share: Int) {
    if (share <= 0) return
    val top = size.height * (1f - share.coerceAtMost(PERCENT) / PERCENT.toFloat())
    val unit = WAVE_DP.dp.toPx()
    val path = Path().apply {
        val (x0, y0) = WAVE.first()
        moveTo(x0 * size.width, top + y0 * unit)
        for ((a, b) in WAVE.zipWithNext()) {
            val midX = (a.first + b.first) / 2 * size.width
            quadraticTo(midX, top + a.second * unit, b.first * size.width, top + b.second * unit)
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(
                WetPatchColor.copy(alpha = WET_PATCH_LIGHTEST_ALPHA),
                WetPatchColor.copy(alpha = WET_PATCH_DARKEST_ALPHA),
            ),
            startY = top,
            endY = size.height,
        ),
    )
}

private const val PERCENT = 100

/** The open cloth's dashed hem, 8 dp above the bottom edge, inside [inset] each side. */
internal fun DrawScope.hem(ink: Color, inset: Float) {
    val y = size.height - HEM_FROM_BOTTOM.toPx()
    drawLine(
        ink.copy(alpha = HEM_ALPHA),
        Offset(inset, y),
        Offset(size.width - inset, y),
        strokeWidth = HEM_WIDTH.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(HEM_DASH.toPx(), HEM_GAP.toPx())),
    )
}

private fun DrawScope.pegLefts(): List<Float> =
    listOf(PEG_INSET.toPx(), size.width - PEG_INSET.toPx() - PEG_WIDTH.toPx())

/** The two pegs at the top corners, clipping the cloth to the line. */
internal fun DrawScope.clothPegs(wood: Color) {
    for (left in pegLefts()) drawPeg(Offset(left, -PEG_RISE.toPx()), wood)
}

/** One peg, 8 by 19 dp, with its dark edge and the split down its middle. */
internal fun DrawScope.drawPeg(topLeft: Offset, wood: Color) {
    val size = Size(PEG_WIDTH.toPx(), PEG_HEIGHT.toPx())
    val corner = CornerRadius(PEG_CORNER.toPx())
    drawRoundRect(wood, topLeft, size, corner)
    val edge = PEG_EDGE.toPx()
    drawRoundRect(
        Color.Black.copy(alpha = PEG_EDGE_ALPHA),
        topLeft + Offset(edge / 2, edge / 2),
        Size(size.width - edge, size.height - edge),
        corner,
        style = Stroke(edge),
    )
    drawRect(
        Color.Black.copy(alpha = PEG_SPLIT_ALPHA),
        topLeft + Offset(PEG_SPLIT_LEFT.toPx(), PEG_SPLIT_TOP.toPx()),
        Size(PEG_SPLIT_WIDTH.toPx(), PEG_SPLIT_HEIGHT.toPx()),
    )
}
