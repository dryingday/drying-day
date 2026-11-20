package io.github.dryingday.forecast

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.dryingday.engine.Load
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.load_heavy
import io.github.dryingday.shared.resources.load_heavy_hint
import io.github.dryingday.shared.resources.load_light
import io.github.dryingday.shared.resources.load_light_hint
import io.github.dryingday.shared.resources.load_note
import io.github.dryingday.shared.resources.load_note_default
import io.github.dryingday.shared.resources.load_note_line
import io.github.dryingday.shared.resources.load_toggle_heavy
import io.github.dryingday.shared.resources.load_toggle_light
import io.github.dryingday.shared.resources.note_collapsed
import io.github.dryingday.shared.resources.note_expanded
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The notes that open and the load toggle. See docs/bolts/08-fewer-words.md, "Notes that open" and "The load toggle".

private const val STACK_FROM_FONT_SCALE = 1.5f
private val MIN_TARGET = 48.dp

/**
 * A line of text with a mark after it that shows or hides [content] when tapped. One node for a screen reader: a
 * button labelled with the line, with its state; the mark is for the eye.
 */
@Composable
internal fun Note(
    title: StringResource,
    open: Boolean,
    onToggle: () -> Unit,
    body: TextStyle,
    content: @Composable () -> Unit,
) {
    val state = stringResource(if (open) Res.string.note_expanded else Res.string.note_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MIN_TARGET)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(stringResource(title), style = body.copy(textDecoration = TextDecoration.Underline))
            BasicText(if (open) "▾" else "▸", Modifier.clearAndSetSemantics {}, style = body)
        }
        if (open) content()
    }
}

/** Bolt 5's two hints, each led by its load, and why heavy is the default. */
@Composable
internal fun LoadNote(open: Boolean, onToggle: () -> Unit, body: TextStyle) {
    Note(Res.string.load_note, open, onToggle, body) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(
                stringResource(
                    Res.string.load_note_line,
                    stringResource(Res.string.load_toggle_light),
                    stringResource(Res.string.load_light_hint),
                ),
                style = body,
            )
            BasicText(
                stringResource(
                    Res.string.load_note_line,
                    stringResource(Res.string.load_toggle_heavy),
                    stringResource(Res.string.load_heavy_hint),
                ),
                style = body,
            )
            BasicText(stringResource(Res.string.load_note_default), style = body)
        }
    }
}

/**
 * Two halves side by side, light to heavy from left to right, the weights on the outer ends. The chosen half has a
 * border and bold text, so it isn't carried by colour alone. At large font sizes the halves stack, light first,
 * each with its weight at the start.
 */
@Composable
internal fun LoadToggle(selected: Load, onSelect: (Load) -> Unit, body: TextStyle) {
    val stacked = LocalDensity.current.fontScale >= STACK_FROM_FONT_SCALE
    val half = @Composable { load: Load, modifier: Modifier ->
        LoadHalf(load, load == selected, stacked, body, modifier) { onSelect(load) }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            half(Load.LIGHT, Modifier.fillMaxWidth())
            half(Load.HEAVY, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            half(Load.LIGHT, Modifier.weight(1f))
            half(Load.HEAVY, Modifier.weight(1f))
        }
    }
}

@Composable
@Suppress("LongParameterList") // Each is passed in by name from the screen.
private fun LoadHalf(
    load: Load,
    selected: Boolean,
    stacked: Boolean,
    body: TextStyle,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val spoken = stringResource(if (load == Load.LIGHT) Res.string.load_light else Res.string.load_heavy)
    val name = stringResource(if (load == Load.LIGHT) Res.string.load_toggle_light else Res.string.load_toggle_heavy)
    val weightFirst = load == Load.LIGHT || stacked
    val outline = if (selected) Modifier.border(2.dp, body.color) else Modifier
    Row(
        modifier.heightIn(min = MIN_TARGET).then(outline)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            if (load == Load.LIGHT ||
                stacked
            ) {
                Alignment.Start
            } else {
                Alignment.End
            },
        ),
    ) {
        val label = @Composable {
            BasicText(
                name,
                Modifier.clearAndSetSemantics {},
                style = body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            )
        }
        if (weightFirst) Picture(load.picture(), SMALL_PICTURE, body.color)
        label()
        if (!weightFirst) Picture(load.picture(), SMALL_PICTURE, body.color)
    }
}
