package io.github.dryingday.forecast

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import io.github.dryingday.GarmentColors
import io.github.dryingday.Palette

// The garments on the row of washing, drawn from Drawings.kt: filled with a garment colour, outlined in the cloth
// ink, each with its own pegs. Decorative: the node they're drawn in reads the sentence.
// See docs/bolts/09-washing-line.md, "The row of washing".

private const val OUTLINE_WIDTH = 1.3f
private val SMALL_PEG_WIDTH = 3.5.dp
private val SMALL_PEG_HEIGHT = 8.dp
private val SMALL_PEG_RISE = 4.dp
private val SMALL_PEG_NUDGE = 1.dp
private val SMALL_PEG_CORNER = 1.dp
private val SMALL_PEG_EDGE = 0.75.dp
private const val SMALL_PEG_EDGE_ALPHA = 0.35f

/** The garments, drawn from the left of a node that starts [spanStart] dp along the row. */
@Composable
internal fun Washing(garments: List<Garment>, spanStart: Float, palette: Palette, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            for (garment in garments) garment(garment, (garment.x - spanStart).dp.toPx(), palette)
        },
    )
}

private fun GarmentKind.drawing(): Drawing = when (this) {
    GarmentKind.T_SHIRT -> Drawings.tee
    GarmentKind.TROUSERS -> Drawings.trousers
    GarmentKind.SHORTS -> Drawings.shorts
    GarmentKind.SOCK -> Drawings.sock
    GarmentKind.TEA_TOWEL -> Drawings.towel
}

private fun DrawScope.garment(garment: Garment, left: Float, palette: Palette) {
    val drawing = garment.kind.drawing()
    val dp = 1.dp.toPx()
    translate(left, 0f) {
        scale(dp, dp, Offset.Zero) {
            drawPath(drawing.layer("fill").path, GarmentColors[garment.colour % GarmentColors.size])
            drawPath(
                drawing.layer("line").path,
                palette.garmentOutline,
                style = Stroke(OUTLINE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        for (peg in garment.kind.pegs) {
            washingPeg(
                Offset(peg.dp.toPx() + SMALL_PEG_NUDGE.toPx(), -SMALL_PEG_RISE.toPx()),
                palette.pegWood,
            )
        }
    }
}

private fun DrawScope.washingPeg(topLeft: Offset, wood: Color) {
    val size = Size(SMALL_PEG_WIDTH.toPx(), SMALL_PEG_HEIGHT.toPx())
    val corner = CornerRadius(SMALL_PEG_CORNER.toPx())
    drawRoundRect(wood, topLeft, size, corner)
    val edge = SMALL_PEG_EDGE.toPx()
    drawRoundRect(
        Color.Black.copy(alpha = SMALL_PEG_EDGE_ALPHA),
        topLeft + Offset(edge / 2, edge / 2),
        Size(size.width - edge, size.height - edge),
        corner,
        style = Stroke(edge),
    )
}
