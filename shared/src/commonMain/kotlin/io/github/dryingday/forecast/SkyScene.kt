package io.github.dryingday.forecast

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.dryingday.Palette
import io.github.dryingday.Sky
import io.github.dryingday.bodyStyle

// The sky at the top of the main screen: its colour by verdict, its picture and rain below the place's row, the
// place, and the line with its cloths. See docs/bolts/09-washing-line.md, "The main screen" and "Motion".

private const val SKY_FADE_MS = 400
private const val RAIN_FADE_MS = 500
private const val RAIN_ALPHA = 0.38f
private const val RING_ALPHA = 0.28f
private const val RAIN_CLOUD_ALPHA = 0.55f

/** The space of sky between the place's row and the line, where the picture shows above the cloths. */
private val LINE_GAP = 36.dp

/** The picture's top, below the place's row. */
private val PICTURE_TOP = 2.dp

/** The sky pictures are drawn on a 340-unit-wide sky, scaled to the screen's width but no larger than this. */
private const val PICTURE_MAX_DP_PER_UNIT = 1.15f
private const val SKY_UNITS_WIDE = 340f

/** The sky's colours for a [kind] of day, from the palette. */
internal fun Palette.sky(kind: SkyKind): Sky = when (kind) {
    SkyKind.WILL_DRY -> willDrySky
    SkyKind.PARTLY_DRY -> partlyDrySky
    SkyKind.DONT_BOTHER -> dontBotherSky
}

/**
 * Milliseconds since it started, ticking every frame while [running]; it holds where it was when it stops. Read it
 * only when drawing, so a tick redraws without recomposing.
 */
@Composable
internal fun rememberFrameClock(running: Boolean): State<Long> {
    val millis = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val from = withFrameMillis { it } - millis.longValue
        while (true) withFrameMillis { millis.longValue = it - from }
    }
    return millis
}

/**
 * The sky: [sky]'s colour from the top edge to just below the cloths, crossfading when the day changes. The place
 * ([placeLabel], tapped to [onPlaces]) on the plain sky in its ink; below it the picture and the rain, clipped so
 * no text sits over them, and [line].
 */
@Composable
@Suppress("LongParameterList") // Each is passed in by name from the screen.
internal fun SkyScene(
    sky: SkyChoice,
    palette: Palette,
    animations: Boolean,
    clock: State<Long>,
    placeLabel: String,
    onPlaces: () -> Unit,
    line: @Composable (ink: () -> Color) -> Unit,
) {
    val colours = palette.sky(sky.kind)
    val colourSpec = if (animations) tween<Color>(SKY_FADE_MS) else snap()
    val background by animateColorAsState(colours.sky, colourSpec, label = "sky")
    val ink by animateColorAsState(colours.ink, colourSpec, label = "skyInk")
    val rainAlpha by animateFloatAsState(
        if (sky.rain) 1f else 0f,
        if (animations) tween(RAIN_FADE_MS) else snap(),
        label = "rain",
    )
    Column(Modifier.fillMaxWidth().drawBehind { drawRect(background) }) {
        Box(
            Modifier.windowInsetsPadding(
                WindowInsets.statusBars.union(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                ),
            )
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onPlaces),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                placeLabel,
                style = bodyStyle().copy(
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            )
        }
        Box(
            Modifier.fillMaxWidth().clipToBounds().drawBehind {
                skyPicture(sky.picture, palette)
                if (rainAlpha > 0f) rain(if (animations) clock.value else 0L, RAIN_ALPHA * rainAlpha)
            }.padding(top = LINE_GAP),
        ) { line { ink } }
    }
}

private fun DrawScope.skyPicture(picture: SkyPicture, palette: Palette) {
    val drawing = when (picture) {
        SkyPicture.SUN -> Drawings.skySun
        SkyPicture.SUN_BEHIND_CLOUD -> Drawings.skySunCloud
        SkyPicture.RAIN_CLOUD -> Drawings.skyRainCloud
        SkyPicture.NONE -> return
    }
    val unit = minOf(size.width / SKY_UNITS_WIDE, PICTURE_MAX_DP_PER_UNIT.dp.toPx())
    val top = drawing.layers.minOf { it.path.getBounds().top }
    translate(size.width - SKY_UNITS_WIDE * unit, PICTURE_TOP.toPx() - top * unit) {
        scale(unit, unit, Offset.Zero) {
            for (layer in drawing.layers) {
                val (colour, alpha) = when (layer.role) {
                    "ring" -> palette.sun to RING_ALPHA

                    "sun" -> palette.sun to 1f

                    else -> if (picture ==
                        SkyPicture.RAIN_CLOUD
                    ) {
                        palette.darkCloud to RAIN_CLOUD_ALPHA
                    } else {
                        palette.cloud to 1f
                    }
                }
                drawPath(layer.path, colour, alpha = alpha)
            }
        }
    }
}

/** A layer of rain: a tile of streaks, falling down its slope (1 across to 4 down) at [speed] dp a second. */
@Suppress("LongParameterList") // One number each, as the mock has them.
private class RainLayer(
    val tileWidth: Float,
    val tileHeight: Float,
    val streaks: List<Offset>,
    val length: Offset,
    val stroke: Float,
    val alpha: Float,
    val speed: Float,
)

// The mock's two tiles: the near streaks fall at 320 dp/s, the far ones at half that.
@Suppress("MagicNumber")
private val RAIN_LAYERS = listOf(
    RainLayer(
        40f,
        160f,
        listOf(Offset(30f, 8f), Offset(12f, 58f), Offset(36f, 96f), Offset(20f, 128f)),
        Offset(-4f, 16f),
        1.2f,
        1f,
        320f,
    ),
    RainLayer(
        80f,
        320f,
        listOf(Offset(58f, 24f), Offset(20f, 130f), Offset(70f, 220f), Offset(38f, 286f)),
        Offset(-5f, 20f),
        1f,
        0.7f,
        160f,
    ),
)

private const val SLOPE = 4f
private const val MILLIS = 1000f

/** Thin white streaks in two layers, at [alpha] overall, where they are at [timeMs]. */
private fun DrawScope.rain(timeMs: Long, alpha: Float) {
    val dp = 1.dp.toPx()
    for (layer in RAIN_LAYERS) {
        val tileWidth = layer.tileWidth * dp
        val tileHeight = layer.tileHeight * dp
        val fallen = layer.speed * dp * timeMs / MILLIS
        val y0 = fallen % tileHeight - tileHeight
        val x0 = -(fallen / SLOPE) % tileWidth
        var y = y0
        while (y < size.height) {
            var x = x0 - tileWidth
            while (x < size.width + tileWidth) {
                for (streak in layer.streaks) {
                    val from = Offset(x + streak.x * dp, y + streak.y * dp)
                    drawLine(
                        Color.White.copy(alpha = alpha * layer.alpha),
                        from,
                        from + layer.length * dp,
                        strokeWidth = layer.stroke * dp,
                        cap = StrokeCap.Round,
                    )
                }
                x += tileWidth
            }
            y += tileHeight
        }
    }
}
