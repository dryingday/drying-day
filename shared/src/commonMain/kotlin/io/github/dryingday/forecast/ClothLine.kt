package io.github.dryingday.forecast

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.dryingday.Palette
import io.github.dryingday.bodyStyle
import io.github.dryingday.dayNameStyle
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.cloth_spoken
import io.github.dryingday.shared.resources.verdict_day_over
import io.github.dryingday.shared.resources.verdict_dont_bother
import io.github.dryingday.shared.resources.verdict_partly_dry
import io.github.dryingday.shared.resources.verdict_will_dry
import io.github.dryingday.verdictStyle
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

// The washing line in the sky and its three cloths: the layout that gives them one height, the widths that move
// when a day is chosen, stacking, and the order a screen reader reads them in. See docs/bolts/09-washing-line.md,
// "The line and the cloths".

private val LINE_THICKNESS = 2.dp
private const val LINE_ALPHA = 0.8f
private val SIDE = 16.dp
private val STACK_GAP = 32.dp

/** The sky below the lowest cloth, where the drips fall. */
private val DRIP_ROOM = 16.dp

/** The sky below an empty line. */
private val EMPTY_LINE_ROOM = 24.dp

private val PRINT_OFFSET = 2.dp
private const val OPEN_MS = 400

/** The mock's ease for the widths, cubic-bezier(.3, .7, .2, 1). */
private val OPEN_EASING = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)

/** The text styles and colours of the cloths, worked out once for the line. */
internal class ClothLook(
    val palette: Palette,
    val name: TextStyle,
    val verdict: TextStyle,
    val body: TextStyle,
    val animations: Boolean,
    val clock: State<Long>,
)

/**
 * The line across the sky with [days]' cloths on it, [selected] open. Empty [days] is the empty line. Tapping a
 * small cloth chooses it through [onSelect] and shakes it; tapping the open one shakes it again. [lineInk] is read
 * when drawing, so the sky's crossfade only redraws the line.
 */
@Composable
@Suppress("LongParameterList") // Each is passed in by name from the screen.
internal fun ClothLine(
    days: List<ClothDay>,
    today: LocalDate?,
    selected: Int,
    onSelect: (Int) -> Unit,
    lineInk: () -> Color,
    look: ClothLook,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val measurer = rememberTextMeasurer()
    val words = VERDICTS.map { stringResource(it) }.flatMap { it.split(' ') }
    val longestWord = remember(words, look.verdict, density) {
        words.maxOfOrNull { measurer.measure(it, look.verdict, softWrap = false, maxLines = 1).size.width } ?: 0
    }
    val spec = if (look.animations) tween<Float>(OPEN_MS, easing = OPEN_EASING) else snap()
    val openness = List(CLOTH_DAYS) { animateFloatAsState(if (it == selected) 1f else 0f, spec, label = "cloth$it") }
    val shakes = remember { mutableStateListOf(0, 0, 0) }
    val spoken = days.map { day -> spokenLabel(day, checkNotNull(today)) }
    val rule = @Composable { Box(Modifier.drawBehind { drawRect(lineInk().copy(alpha = LINE_ALPHA)) }) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sizes = clothSizes(density)
        val width = constraints.maxWidth
        val start = insets.getLeft(density, direction) + sizes.side
        val inner = (width - start - insets.getRight(density, direction) - sizes.side).coerceAtLeast(0)
        val sideBySide = openClothWidth(inner.toFloat(), sizes.small, sizes.gap.toFloat())
        val stacked = with(density) {
            days.isNotEmpty() &&
                stackCloths(fontScale, (sideBySide - sizes.padding).toDp().value, longestWord.toDp().value)
        }
        val openWidth = if (stacked) inner.toFloat() else sideBySide
        val plan = LinePlan(sizes, width, start, inner, openWidth, selected)
        Layout(
            contents = listOf(
                { repeat(if (stacked) days.size else 1) { rule() } },
                { for (day in days) Box(Modifier.clearAndSetSemantics {}) { OpenText(day, look, null) } },
                {
                    for (day in days) {
                        val open = day.index == selected
                        Cloth(day, open, stacked, plan.contentWidth, look, shakes[day.index], spoken[day.index]) {
                            if (!open) onSelect(day.index)
                            shakes[day.index] = shakes[day.index] + 1
                        }
                    }
                },
            ),
            modifier = Modifier.semantics { isTraversalGroup = true }.selectableGroup(),
        ) { (lines, probes, cloths), _ ->
            val rules = lines.map { it.measure(Constraints.fixed(width, sizes.line)) }
            when {
                days.isEmpty() -> layout(width, sizes.line + sizes.emptyRoom) { rules.first().place(0, 0) }
                stacked -> stackedLine(plan, rules, cloths, plan.height(probes))
                else -> sideBySideLine(plan, rules, cloths, plan.height(probes)) { openness[it].value }
            }
        }
    }
}

/** What the line's layout is worked out from, once its width is known. */
private class LinePlan(
    val sizes: ClothSizes,
    val width: Int,
    val start: Int,
    val inner: Int,
    val openWidth: Float,
    val selected: Int,
) {
    /** The open cloth's text width: its final width, less its padding. */
    val contentWidth = (openWidth.toInt() - sizes.padding).coerceAtLeast(0)

    /** The one height: the tallest day's open content at [contentWidth], with the cloth's padding. */
    fun height(probes: List<Measurable>): Int = sharedClothHeight(
        probes.map {
            it.measure(Constraints(minWidth = contentWidth, maxWidth = contentWidth)).height + sizes.verticalPadding
        },
        sizes.minHeight,
    )

    fun zIndex(index: Int): Float = if (index == selected) 1f else 0f
}

// One under another, each with its own line above it: the open one at the shared height, the others their own.
private fun MeasureScope.stackedLine(
    plan: LinePlan,
    rules: List<Placeable>,
    cloths: List<Measurable>,
    height: Int,
): MeasureResult {
    val sizes = plan.sizes
    val placed = cloths.mapIndexed { i, cloth ->
        cloth.measure(
            if (i == plan.selected) {
                Constraints.fixed(plan.inner, height)
            } else {
                Constraints(plan.inner, plan.inner, sizes.stackedMin, Constraints.Infinity)
            },
        )
    }
    val tops = placed.runningFold(0) { y, cloth -> y + sizes.line + cloth.height + sizes.stackGap }
    return layout(plan.width, tops.last() - sizes.stackGap + sizes.dripRoom) {
        placed.forEachIndexed { i, cloth ->
            rules[i].place(0, tops[i])
            cloth.place(plan.start, tops[i] + sizes.line, zIndex = plan.zIndex(i))
        }
    }
}

// Side by side on one line, each as wide as it is open, read while it moves.
private fun MeasureScope.sideBySideLine(
    plan: LinePlan,
    rules: List<Placeable>,
    cloths: List<Measurable>,
    height: Int,
    openness: (Int) -> Float,
): MeasureResult {
    val sizes = plan.sizes
    val placed = cloths.mapIndexed { i, cloth ->
        cloth.measure(Constraints.fixed(clothWidth(openness(i), sizes.small, plan.openWidth), height))
    }
    return layout(plan.width, sizes.line + height + sizes.dripRoom) {
        rules.first().place(0, 0)
        var x = plan.start
        placed.forEachIndexed { i, cloth ->
            cloth.place(x, sizes.line, zIndex = plan.zIndex(i))
            x += cloth.width + sizes.gap
        }
    }
}

private fun clothSizes(density: Density): ClothSizes = with(density) {
    ClothSizes(
        small = SMALL_CLOTH_DP.dp.toPx(),
        gap = CLOTH_GAP_DP.dp.roundToPx(),
        side = SIDE.roundToPx(),
        line = LINE_THICKNESS.roundToPx(),
        padding = (OPEN_SIDE_PADDING * 2).roundToPx(),
        minHeight = MIN_CLOTH_HEIGHT_DP.dp.roundToPx(),
        verticalPadding = (CLOTH_TOP_PADDING + OPEN_BOTTOM_PADDING).roundToPx(),
        stackedMin = STACKED_MIN_HEIGHT.roundToPx(),
        stackGap = STACK_GAP.roundToPx(),
        dripRoom = DRIP_ROOM.roundToPx(),
        emptyRoom = EMPTY_LINE_ROOM.roundToPx(),
    )
}

private val VERDICTS =
    listOf(
        Res.string.verdict_will_dry,
        Res.string.verdict_partly_dry,
        Res.string.verdict_dont_bother,
        Res.string.verdict_day_over,
    )

/**
 * What a screen reader says for [day]'s cloth, through [clothSpoken]: its messages are resolved here first, as
 * resolving one is composable.
 */
@Composable
private fun spokenLabel(day: ClothDay, today: LocalDate): String {
    val name = stringResource(day.fullName)
    val verdict = day.headline?.let { stringResource(it) }
    val both = verdict?.let { stringResource(Res.string.cloth_spoken, name, it) }
    return clothSpoken(today, day.index, day.advice) { message ->
        when (message.resource) {
            Res.string.cloth_spoken -> checkNotNull(both)
            day.headline -> checkNotNull(verdict)
            else -> name
        }
    }
}

/** The line's measurements in pixels, from dp once per width. */
@Suppress("LongParameterList") // One measurement each.
private class ClothSizes(
    val small: Float,
    val gap: Int,
    val side: Int,
    val line: Int,
    val padding: Int,
    val minHeight: Int,
    val verticalPadding: Int,
    val stackedMin: Int,
    val stackGap: Int,
    val dripRoom: Int,
    val emptyRoom: Int,
)

private val STACKED_MIN_HEIGHT = 56.dp

/** The cloths' styles: the cloth ink, and the verdict's second print in the laundry blue. */
@Composable
internal fun clothLook(palette: Palette, animations: Boolean, clock: State<Long>): ClothLook {
    val density = LocalDensity.current
    val print = with(density) { PRINT_OFFSET.toPx() }
    val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
    return ClothLook(
        palette = palette,
        name = dayNameStyle().copy(color = palette.clothInk, lineHeight = 1.em, lineHeightStyle = tight),
        verdict = verdictStyle().copy(
            color = palette.clothInk,
            shadow = Shadow(palette.secondPrint, Offset(print, print), 0f),
        ),
        body = bodyStyle().copy(color = palette.clothInk),
        animations = animations,
        clock = clock,
    )
}
