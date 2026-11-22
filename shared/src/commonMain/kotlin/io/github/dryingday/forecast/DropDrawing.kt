package io.github.dryingday.forecast

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import io.github.dryingday.Palette

// The drops: the drips under a wet cloth and the spray its shake throws, from [Drawings.drop] in the drop colours.
// Decorative. See docs/bolts/09-washing-line.md, "Motion".

private val DROP_RISE = 3.dp
private val DROP_HALF_WIDTH = 4.5.dp
private const val DROP_EDGE_WIDTH = 1.2f

/** A drop from [Drawings.drop], its top centre at [top], scaled about that point, in the drop colours. */
internal fun DrawScope.drop(top: Offset, frame: DropFrame, palette: Palette) {
    if (frame.alpha <= 0f) return
    val dp = 1.dp.toPx()
    translate(top.x, top.y + frame.y * dp) {
        scale(frame.scaleX * dp, frame.scaleY * dp, Offset.Zero) {
            translate(-Drawings.drop.width / 2, 0f) { dropShape(palette, frame.alpha) }
        }
    }
}

/** A spray drop: centred on [centre], turned and scaled about it. */
internal fun DrawScope.sprayDrop(centre: Offset, frame: SprayFrame, palette: Palette) {
    val dp = 1.dp.toPx()
    val at = centre + Offset(frame.dx * dp, frame.dy * dp)
    rotate(frame.rotation, at) {
        translate(at.x, at.y) {
            scale(frame.scale * dp, frame.scale * dp, Offset.Zero) {
                translate(-Drawings.drop.width / 2, -Drawings.drop.height / 2) { dropShape(palette, frame.alpha) }
            }
        }
    }
}

// In the drop's own units (dp before scaling).
private fun DrawScope.dropShape(palette: Palette, alpha: Float) {
    drawPath(Drawings.drop.layer("fill").path, palette.drop, alpha = alpha)
    drawPath(Drawings.drop.layer("line").path, palette.dropEdge, alpha = alpha, style = Stroke(DROP_EDGE_WIDTH))
}

/** Where a drip hangs from the bottom edge of a cloth this size: [at] of the way across, just over the hem. */
internal fun DrawScope.dripTop(at: Float): Offset = Offset(
    (size.width * at).coerceIn(DROP_HALF_WIDTH.toPx(), size.width - DROP_HALF_WIDTH.toPx()),
    size.height - DROP_RISE.toPx(),
)
