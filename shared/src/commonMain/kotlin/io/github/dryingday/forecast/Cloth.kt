package io.github.dryingday.forecast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

// One cloth on the line: drawn (weave, shadow, pegs, wet patch, drips), its tap target, its text, and its shake.
// See docs/bolts/09-washing-line.md, "The line and the cloths" and "Motion".

internal val CLOTH_TOP_PADDING = 14.dp
internal val OPEN_SIDE_PADDING = 12.dp
private val SMALL_SIDE_PADDING = 6.dp

/** Below the open cloth's text: 8 dp to the hem, the 2 dp hem, and 8 dp to the bottom edge. */
internal val OPEN_BOTTOM_PADDING = 18.dp
private val VERDICT_GAP = 4.dp
private val SENTENCE_GAP = 6.dp
private const val REVEAL_MS = 300
private const val REVEAL_DELAY_MS = 180
private const val SPRAY_SEED_STEP = 31

// A screen reader reads the three tabs first, then the open cloth's verdict and sentence.
private const val TAB_ORDER = 0f
private const val TEXT_ORDER = 1f

/** A shake under way: its curve and the drops it throws. */
private class Shaking(val shake: Shake, val spray: List<SprayDrop>)

/** A cloth's shake: [elapsed] milliseconds into it, and what it is while one runs. */
private class ShakeState {
    val elapsed = Animatable(0f)
    var shaking by mutableStateOf<Shaking?>(null)

    suspend fun run(shake: Shake, spray: List<SprayDrop>) {
        shaking = Shaking(shake, spray)
        val end = if (spray.isEmpty()) shake.durationMs else maxOf(shake.durationMs, sprayEndMs(spray))
        elapsed.snapTo(0f)
        elapsed.animateTo(end.toFloat(), tween(end, easing = LinearEasing))
        shaking = null
    }
}

/**
 * The cloth for [day], [open] or small, its size set by the line. Its open text is laid out at [contentWidth] (the
 * open cloth's final width, less its padding) from the start, so it never re-wraps as the cloth widens; it's
 * revealed as the cloth widens and fades in. Each new [shakeCount] shakes it. In a [stacked] line, a small cloth
 * shows its full name.
 */
@Composable
@Suppress("LongParameterList") // Each is passed in by name from the line.
internal fun Cloth(
    day: ClothDay,
    open: Boolean,
    stacked: Boolean,
    contentWidth: Int,
    look: ClothLook,
    shakeCount: Int,
    spoken: String,
    onTap: () -> Unit,
) {
    val motion = day.motion
    val shake = remember { ShakeState() }
    // Only a tap made while this cloth is on screen shakes it: when the cloths come back (the forecast
    // failing and returning), the count they come back with has already been shaken.
    val shookAt = remember { shakeCount }
    LaunchedEffect(shakeCount) {
        val curve = motion.shake
        if (shakeCount > shookAt && curve != null) {
            shake.run(curve, sprayDrops(motion.spray, Random(shakeCount * SPRAY_SEED_STEP + day.index)))
        }
    }
    val reveal = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        when {
            !open -> reveal.snapTo(0f)
            look.animations -> reveal.animateTo(1f, tween(REVEAL_MS, delayMillis = REVEAL_DELAY_MS))
            else -> reveal.snapTo(1f)
        }
    }
    val drips = drips(day.advice?.verdict, if (open) motion.dripsOpen else motion.dripsSmall)
    val padding = if (open) OPEN_SIDE_PADDING else SMALL_SIDE_PADDING

    Box(Modifier.clothDrawing(day, open, drips, look, shake)) {
        Box(
            Modifier.matchParentSize()
                .selectable(
                    selected = open,
                    interactionSource = null,
                    indication = LocalIndication.current,
                    role = Role.Tab,
                    onClick = onTap,
                )
                .semantics {
                    contentDescription = spoken
                    traversalIndex = TAB_ORDER
                },
        )
        Column(
            Modifier.fillMaxWidth().clipToBounds()
                .padding(start = padding, end = padding, top = CLOTH_TOP_PADDING, bottom = SMALL_SIDE_PADDING),
        ) {
            if (open) {
                Box(Modifier.laidOutAt(contentWidth)) { OpenText(day, look) { reveal.value } }
            } else {
                BasicText(
                    stringResource(if (stacked) day.fullName else day.shortName),
                    Modifier.clearAndSetSemantics {},
                    style = look.name,
                    softWrap = stacked,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * Everything drawn of a cloth, leant by its shake: the shadow, the cloth and its weave, the wet patch, the hem
 * when [open], the content, the pegs, the [drips] and the spray. All of it reads its moving parts when drawing.
 */
private fun Modifier.clothDrawing(
    day: ClothDay,
    open: Boolean,
    drips: List<Drip>,
    look: ClothLook,
    shake: ShakeState,
): Modifier = drawWithContent {
    val palette = look.palette
    val active = shake.shaking
    val now = shake.elapsed.value
    val angle = active?.let { shakeAngle(it.shake, now / it.shake.durationMs) } ?: 0f
    skewedFromTop(angle) {
        clothShadow()
        clothFace(palette.cloth)
        day.wetShare?.let { wetPatch(it) }
        if (open) hem(palette.clothInk, OPEN_SIDE_PADDING.toPx())
        this@drawWithContent.drawContent()
        clothPegs(palette.pegWood)
        if (drips.isNotEmpty()) {
            val time = look.clock.value
            for (drip in drips) drop(dripTop(drip.at), dripFrame(drip, time, day.motion.dripsFall), palette)
        }
        active?.spray?.forEach { drop ->
            sprayFrame(drop, now.toLong())?.let {
                sprayDrop(Offset(drop.x * size.width, drop.y * size.height), it, palette)
            }
        }
    }
}

/**
 * The open cloth's text: the full name, the verdict as a heading and the sentence, each a node of its own after
 * the tabs. [reveal] is its opacity, read when drawing; null for the measuring pass.
 */
@Composable
internal fun OpenText(day: ClothDay, look: ClothLook, reveal: (() -> Float)?) {
    val shown = Modifier.graphicsLayer { alpha = reveal?.invoke() ?: 1f }
    Column {
        BasicText(stringResource(day.fullName), Modifier.clearAndSetSemantics {}, style = look.name)
        day.headline?.let {
            BasicText(
                stringResource(it),
                Modifier.padding(top = VERDICT_GAP).then(shown).semantics {
                    heading()
                    traversalIndex = TEXT_ORDER
                },
                style = look.verdict,
            )
        }
        if (day.sentence.isNotEmpty()) {
            BasicText(
                day.sentence.map { it.resolve() }.joinToString(" "),
                Modifier.padding(top = SENTENCE_GAP).then(shown).semantics { traversalIndex = TEXT_ORDER },
                style = look.body,
            )
        }
    }
}

/** Measures the content [width] wide whatever room it's given, starting at the left; the cloth clips the rest. */
private fun Modifier.laidOutAt(width: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight),
    )
    layout(minOf(width, constraints.maxWidth), placeable.height) { placeable.place(0, 0) }
}
