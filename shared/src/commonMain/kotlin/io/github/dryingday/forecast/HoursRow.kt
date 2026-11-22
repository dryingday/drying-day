package io.github.dryingday.forecast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryingday.Palette
import io.github.dryingday.bodyStyle
import io.github.dryingday.labelStyle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// The pictures and the hours row of the main screen. See docs/bolts/07-weather-at-a-glance.md, "The screen", and
// docs/bolts/09-washing-line.md, "The row of washing".

internal val SMALL_PICTURE = 24.dp
private val HOUR_COLUMN_MIN_WIDTH = 48.dp
private val TIME_GAP = 8.dp
private val TIME_TO_LINE = 6.dp
private val WASHING_LINE = 2.dp

/** The garments hang from just inside the line's top edge. */
private val GARMENT_DROP = 1.dp

/** The tallest garment, the trousers: the space above the hours grows to fit them. */
private val GARMENT_ROOM = GarmentKind.entries.maxOf { it.height }.dp
private val WASHING_TO_COLUMNS = 8.dp

@Composable
@Suppress("SpreadOperator") // A handful of strings, resolved once each.
internal fun Message.resolve(): String = stringResource(resource, *args.toTypedArray())

// Decorative: the written verdict or condition is always next to it, or in the column's description.
@Composable
internal fun Picture(picture: DrawableResource, size: Dp, tint: Color) {
    Image(
        painterResource(picture),
        contentDescription = null,
        modifier = Modifier.size(size).clearAndSetSemantics {},
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * The row of washing and the hours row, in one sideways scroll. The columns share one width, the widest column's,
 * so a time maps to a place on the row; the washing and its times sit above them, placed where bolt 8's bar was.
 * The row opens with the washing's start in view. See docs/bolts/09-washing-line.md, "The row of washing".
 */
@Composable
@Suppress("LongParameterList") // Each is passed in by name from the screen.
internal fun HoursRow(
    columns: List<HourColumn>,
    window: Window?,
    zone: TimeZone,
    formatTime: TimeFormat,
    spokenTimes: String,
    seed: LocalDate,
    palette: Palette,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var columnPx by remember { mutableIntStateOf(0) }
    val opening = openingColumn(columns, window)
    // Once, when the columns are first measured: a later refresh or tick of the clock doesn't snap the row back.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(columnPx) {
        if (columnPx > 0 && !opened) {
            opened = true
            scroll.scrollTo(opening * columnPx)
        }
    }
    val body = bodyStyle().copy(color = palette.ink)
    val small = labelStyle().copy(color = palette.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        SubcomposeLayout { constraints ->
            val loose = Constraints()
            val minPx = HOUR_COLUMN_MIN_WIDTH.roundToPx()
            val widest = subcompose("probe") { columns.forEach { HourColumnView(it, null, body) } }
                .maxOfOrNull { it.measure(loose).width } ?: 0
            val width = maxOf(minPx, widest)
            if (width != columnPx) columnPx = width
            val widthDp = with(density) { width.toDp() }
            val rowWidth = width * columns.size
            val rowWidthDp = with(density) { rowWidth.toDp() }.value
            val span = barSpan(columns, window, widthDp.value, rowWidthDp)
            val washing =
                measureWashing(
                    WashingRequest(span, window, zone, formatTime, spokenTimes, small, palette, seed, rowWidthDp),
                    density,
                    rowWidth,
                )
            // Subcomposed after the washing, so a screen reader reads it and its times before the columns.
            val cells = subcompose("columns") { columns.forEach { HourColumnView(it, widthDp, body) } }
                .map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val columnsTop = washing.height
            layout(rowWidth, columnsTop + (cells.maxOfOrNull { it.height } ?: 0)) {
                washing.place(this)
                cells.forEachIndexed { i, cell -> cell.placeRelative(i * width, columnsTop) }
            }
        }
    }
}

@Suppress("LongParameterList") // Everything the washing is drawn from, in one place.
private class WashingRequest(
    val span: BarSpan?,
    val window: Window?,
    val zone: TimeZone,
    val formatTime: TimeFormat,
    val spokenTimes: String,
    val timeStyle: TextStyle,
    val palette: Palette,
    val seed: LocalDate,
    val rowWidthDp: Float,
)

private class MeasuredWashing(val height: Int, val place: Placeable.PlacementScope.() -> Unit)

private val NO_WASHING = MeasuredWashing(0) {}

// The washing, and its times above it: the hang-out time over its start and the bring-in time over its end, or,
// when the two don't fit side by side over it with a gap, one label centred over it and kept in the row. Under
// the times, a line the row's full width; the garments hang from it between bolt 8's bar ends. The washing and
// its times are one node, reading the sentence; the times and the line are decorative.
@Suppress("LongMethod") // The times, the line and the washing, measured and placed together.
private fun SubcomposeMeasureScope.measureWashing(
    request: WashingRequest,
    density: Density,
    rowWidth: Int,
): MeasuredWashing {
    val span = request.span
    val window = request.window
    if (span == null || window == null) return NO_WASHING
    with(density) {
        val loose = Constraints()
        val start = span.start.dp.roundToPx()
        val end = span.end.dp.roundToPx()
        val time = { slot: String, text: String ->
            subcompose(slot) {
                BasicText(text, Modifier.clearAndSetSemantics {}, style = request.timeStyle)
            }.first().measure(loose)
        }
        val hang = time("hang", request.formatTime(window.hangOut, request.zone))
        val bring = time("bring", request.formatTime(window.bringIn, request.zone))
        val together = window.hangOut == window.bringIn || hang.width + TIME_GAP.roundToPx() + bring.width > end - start
        val label = if (together) {
            subcompose("label") {
                BasicText(
                    barLabel(window, request.zone, request.formatTime).resolve(),
                    Modifier.clearAndSetSemantics {},
                    style = request.timeStyle,
                )
            }.first().measure(loose)
        } else {
            null
        }
        val textHeight = label?.height ?: maxOf(hang.height, bring.height)
        val lineTop = textHeight + TIME_TO_LINE.roundToPx()
        val line = subcompose("line") {
            Box(Modifier.background(request.palette.muted).clearAndSetSemantics {})
        }.first().measure(Constraints.fixed(rowWidth, WASHING_LINE.roundToPx()))
        val garments = washingRow(span.start, span.end, request.rowWidthDp, request.seed)
        val washing = subcompose("washing") {
            Washing(
                garments,
                span.start,
                request.palette,
                Modifier.clearAndSetSemantics { contentDescription = request.spokenTimes },
            )
        }.first().measure(Constraints.fixed(maxOf(end - start, 1), GARMENT_ROOM.roundToPx()))
        val hangFrom = lineTop + GARMENT_DROP.roundToPx()
        return MeasuredWashing(hangFrom + GARMENT_ROOM.roundToPx() + WASHING_TO_COLUMNS.roundToPx()) {
            if (label != null) {
                label.placeRelative(((start + end - label.width) / 2).coerceIn(0, maxOf(0, rowWidth - label.width)), 0)
            } else {
                hang.placeRelative(start, 0)
                bring.placeRelative(end - bring.width, 0)
            }
            line.placeRelative(0, lineTop)
            washing.placeRelative(start, hangFrom)
        }
    }
}

// One semantics node for the whole column. A column outside the drying window says nothing about it.
@Composable
private fun HourColumnView(column: HourColumn, width: Dp?, body: TextStyle) {
    val text = body.color
    val parts = column.spokenParts().map { it.resolve() }
    val description = (listOf(column.time) + parts).joinToString(", ")
    val small = body.copy(fontSize = 13.sp, textAlign = TextAlign.Center)
    val sizing = if (width ==
        null
    ) {
        Modifier.width(IntrinsicSize.Max).widthIn(min = HOUR_COLUMN_MIN_WIDTH)
    } else {
        Modifier.width(width)
    }
    Column(
        sizing.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(column.time, style = small)
            val condition = column.condition
            if (condition == null) {
                Box(Modifier.size(SMALL_PICTURE))
            } else {
                Picture(condition.picture(), SMALL_PICTURE, text)
            }
        }
    }
}
