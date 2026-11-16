package io.github.dryingday.forecast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// The pictures and the hours row of the main screen. See docs/bolts/07-weather-at-a-glance.md, "The screen".

internal val SMALL_PICTURE = 24.dp
private val DRYING_BAR = 6.dp
private val HOUR_COLUMN_MIN_WIDTH = 56.dp

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

@Composable
internal fun HoursRow(columns: List<HourColumn>, body: TextStyle) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        for (column in columns) HourColumnView(column, body)
    }
}

// One semantics node for the whole column. The bar is under every column in the drying window, so it joins up
// across neighbours; a column outside it has the same space, empty, so the columns line up. The column takes
// its widest child's width: in a row that scrolls sideways the width is unbounded, and without that the bar's
// fillMaxWidth would measure nothing and never draw.
@Composable
private fun HourColumnView(column: HourColumn, body: TextStyle) {
    val text = body.color
    val parts = column.spokenParts().map { it.resolve() }
    val description = (listOf(column.time) + parts).joinToString(", ")
    val small = body.copy(fontSize = 13.sp, textAlign = TextAlign.Center)
    Column(
        Modifier.width(IntrinsicSize.Max).widthIn(min = HOUR_COLUMN_MIN_WIDTH).clearAndSetSemantics {
            contentDescription =
                description
        },
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
            BasicText(column.temperature, style = small.copy(fontWeight = FontWeight.Bold))
            // Blank, not absent, so every column is as tall as the others.
            BasicText(column.rainChance ?: NO_BREAK_SPACE, style = small)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(
                DRYING_BAR,
            ).background(if (column.inDryingWindow) text else Color.Transparent),
        )
    }
}

private const val NO_BREAK_SPACE = "\u00A0"
