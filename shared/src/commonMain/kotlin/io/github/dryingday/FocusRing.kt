package io.github.dryingday

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val RING_WIDTH = 3.dp
private const val PRESSED_ALPHA = 0.12f

/**
 * The indication for what's tappable on the main screen: a ring in [ring] (the laundry blue) while it has focus, and
 * a faint wash of [press] while it's pressed. Square, like the buttons.
 */
class FocusRing(private val ring: Color, private val press: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingNode(interactionSource, ring, press)

    override fun equals(other: Any?): Boolean = other is FocusRing && other.ring == ring && other.press == press

    override fun hashCode(): Int = 31 * ring.hashCode() + press.hashCode()
}

private class FocusRingNode(private val source: InteractionSource, private val ring: Color, private val press: Color) :
    Modifier.Node(),
    DrawModifierNode {
    private var focused = 0
    private var pressed = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focused++
                    is FocusInteraction.Unfocus -> focused--
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed--
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed > 0) drawRect(press.copy(alpha = PRESSED_ALPHA))
        if (focused > 0) {
            val width = RING_WIDTH.toPx()
            drawRect(
                ring,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width),
            )
        }
    }
}
