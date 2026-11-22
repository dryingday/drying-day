package io.github.dryingday.forecast

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

// The drawings (garments, the drop and the sky pictures), drawn for this app. Sources are the SVGs in art/ with
// the same path data; DrawingsTest keeps them equal. Each layer is one path with a role, and the screen fills or
// strokes it with its palette colour at draw time, so one set serves light and dark: a garment's "fill" is filled
// with the cloth colour and its "line" stroked with the outline colour; the sun's "ring" is the sun colour at
// 28 % and its "sun" the sun colour; a "cloud" is the cloud colour (the rain cloud's at 55 %). Viewport sizes are
// in dp for the garments and the drop, and in the sky's own units for the three skies (340 by 170).
// See docs/bolts/09-washing-line.md, "The drawings".

internal class DrawingLayer(val role: String, val pathData: String) {
    /** The parsed path, built on first use. Shared, so draw it but don't change it. */
    val path: Path by lazy { parsePath(pathData) }
}

internal class Drawing(val name: String, val width: Float, val height: Float, val layers: List<DrawingLayer>) {
    fun layer(role: String): DrawingLayer = layers.first { it.role == role }
}

internal fun parsePath(pathData: String): Path = PathParser().parsePathString(pathData).toPath()

internal object Drawings {
    val tee: Drawing =
        Drawing(
            "garment_tee",
            26f,
            22f,
            listOf(
                DrawingLayer(
                    "fill",
                    "M8.5 1 1 5l2.2 5.2L6 9.2V21h14V9.2l2.8 1L25 5 17.5 1" +
                        "c-1 2.2-2.6 3.2-4.5 3.2S9.5 3.2 8.5 1Z",
                ),
                DrawingLayer(
                    "line",
                    "M8.5 1 1 5l2.2 5.2L6 9.2V21h14V9.2l2.8 1L25 5 17.5 1" +
                        "c-1 2.2-2.6 3.2-4.5 3.2S9.5 3.2 8.5 1Z",
                ),
            ),
        )

    val trousers: Drawing =
        Drawing(
            "garment_trousers",
            18f,
            30f,
            listOf(
                DrawingLayer("fill", "M1 1h16v28h-6.2L9 10.5 7.2 29H1Z"),
                DrawingLayer("line", "M1 1h16v28h-6.2L9 10.5 7.2 29H1Z"),
            ),
        )

    val shorts: Drawing =
        Drawing(
            "garment_shorts",
            20f,
            17f,
            listOf(
                DrawingLayer("fill", "M1 1h18v15h-7l-2-7.5L8 16H1Z"),
                DrawingLayer("line", "M1 1h18v15h-7l-2-7.5L8 16H1Z"),
            ),
        )

    val sock: Drawing =
        Drawing(
            "garment_sock",
            11f,
            20f,
            listOf(
                DrawingLayer("fill", "M2 1h6.5v11.5l1.8 2.2c.9 2.6-.6 4.3-3 4.3H4c-2.2 0-2.6-1.6-2-3.2Z"),
                DrawingLayer("line", "M2 1h6.5v11.5l1.8 2.2c.9 2.6-.6 4.3-3 4.3H4c-2.2 0-2.6-1.6-2-3.2Z"),
            ),
        )

    val towel: Drawing =
        Drawing(
            "garment_towel",
            22f,
            22f,
            listOf(
                DrawingLayer("fill", "M1 1h20v20H1ZM1 16.5h20"),
                DrawingLayer("line", "M1 1h20v20H1ZM1 16.5h20"),
            ),
        )

    val drop: Drawing =
        Drawing(
            "drop",
            9f,
            13f,
            listOf(
                DrawingLayer("fill", "M4.5 .5C4.5 .5 .5 6.1 .5 8.7a4 4 0 0 0 8 0C8.5 6.1 4.5 .5 4.5 .5Z"),
                DrawingLayer("line", "M4.5 .5C4.5 .5 .5 6.1 .5 8.7a4 4 0 0 0 8 0C8.5 6.1 4.5 .5 4.5 .5Z"),
            ),
        )

    val skySun: Drawing =
        Drawing(
            "sky_sun",
            340f,
            170f,
            listOf(
                DrawingLayer("ring", "M216 86A52 52 0 1 1 320 86A52 52 0 1 1 216 86Z"),
                DrawingLayer("sun", "M232 86A36 36 0 1 1 304 86A36 36 0 1 1 232 86Z"),
            ),
        )

    val skySunCloud: Drawing =
        Drawing(
            "sky_sun_cloud",
            340f,
            170f,
            listOf(
                DrawingLayer("sun", "M242 70A28 28 0 1 1 298 70A28 28 0 1 1 242 70Z"),
                DrawingLayer(
                    "cloud",
                    "M226 96A22 22 0 1 1 270 96A22 22 0 1 1 226 96ZM263 92" +
                        "A19 19 0 1 1 301 92A19 19 0 1 1 263 92ZM203 104A15 15 0 1 1 233 104" +
                        "A15 15 0 1 1 203 104ZM213.5 100H290.5A10.5 10.5 0 0 1 301 110.5" +
                        "V110.5A10.5 10.5 0 0 1 290.5 121H213.5A10.5 10.5 0 0 1 203 110.5" +
                        "V110.5A10.5 10.5 0 0 1 213.5 100Z",
                ),
            ),
        )

    val skyRainCloud: Drawing =
        Drawing(
            "sky_rain_cloud",
            340f,
            170f,
            listOf(
                DrawingLayer(
                    "cloud",
                    "M66 38A24 24 0 1 1 114 38A24 24 0 1 1 66 38ZM100 32" +
                        "A28 28 0 1 1 156 32A28 28 0 1 1 100 32ZM148 40A22 22 0 1 1 192 40" +
                        "A22 22 0 1 1 148 40ZM214 30A26 26 0 1 1 266 30A26 26 0 1 1 214 30" +
                        "ZM264 38A22 22 0 1 1 308 38A22 22 0 1 1 264 38ZM73 40" +
                        "H301A11 11 0 0 1 312 51V51A11 11 0 0 1 301 62H73A11 11 0 0 1 62 51" +
                        "V51A11 11 0 0 1 73 40Z",
                ),
            ),
        )

    val all: List<Drawing> = listOf(tee, trousers, shorts, sock, towel, drop, skySun, skySunCloud, skyRainCloud)
}
