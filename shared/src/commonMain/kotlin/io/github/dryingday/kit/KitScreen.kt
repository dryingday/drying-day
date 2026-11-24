package io.github.dryingday.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dryingday.Palette
import io.github.dryingday.bodyStyle
import io.github.dryingday.engine.Equipment
import io.github.dryingday.forecast.text
import io.github.dryingday.headingStyle
import io.github.dryingday.palette
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.kit_intro
import io.github.dryingday.shared.resources.kit_title
import io.github.dryingday.shared.resources.places_back
import org.jetbrains.compose.resources.stringResource

private val BOX_SIZE = 24.dp
private val BOX_BORDER = 2.dp
private val TICK_WIDTH = 3.dp

/** The kit with [item] ticked or unticked; a kit not yet said is taken as empty, so the first tap sets it. */
internal fun toggledKit(kit: Set<Equipment>?, item: Equipment): Set<Equipment> {
    val current = kit.orEmpty()
    return if (item in current) current - item else current + item
}

/**
 * "What you dry with": the four things someone might dry with, each a row to tick. A tap saves straight away through
 * [onKit]. [kit] is null until it has been said, which shows as nothing ticked. See docs/bolts/10-equipment.md.
 */
@Composable
fun KitScreen(kit: Set<Equipment>?, onKit: (Set<Equipment>) -> Unit, onBack: () -> Unit) {
    val colours = palette()
    val body = bodyStyle().copy(color = colours.ink)
    Box(Modifier.fillMaxSize().background(colours.page)) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { BasicText(stringResource(Res.string.places_back), style = body) }
            BasicText(
                stringResource(Res.string.kit_title),
                Modifier.semantics { heading() },
                style = headingStyle().copy(color = colours.ink),
            )
            BasicText(stringResource(Res.string.kit_intro), style = body)
            Column {
                for (item in Equipment.entries) {
                    val checked = kit.orEmpty().contains(item)
                    KitRow(stringResource(item.text()), checked, colours) { onKit(toggledKit(kit, item)) }
                }
            }
        }
    }
}

/** One node for a screen reader: a checkbox with the name as its label. The drawn box has no semantics. */
@Composable
private fun KitRow(name: String, checked: Boolean, colours: Palette, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickBox(checked, colours)
        BasicText(name, Modifier.weight(1f), style = bodyStyle().copy(color = colours.ink))
    }
}

/** A square box in the ink, filled with a tick in the page colour when ticked. Square, like the buttons. */
@Composable
private fun TickBox(checked: Boolean, colours: Palette) {
    val fill = if (checked) Modifier.background(colours.ink) else Modifier
    Canvas(Modifier.size(BOX_SIZE).then(fill).border(BOX_BORDER, colours.ink).clearAndSetSemantics {}) {
        if (checked) {
            val tick = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.52f)
                lineTo(size.width * 0.43f, size.height * 0.72f)
                lineTo(size.width * 0.78f, size.height * 0.30f)
            }
            drawPath(tick, colours.page, style = Stroke(TICK_WIDTH.toPx(), cap = StrokeCap.Square))
        }
    }
}
