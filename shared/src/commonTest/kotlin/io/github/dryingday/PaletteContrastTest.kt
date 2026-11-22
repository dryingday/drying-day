package io.github.dryingday

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every pair of text and its background in docs/bolts/09-washing-line.md is at least 4.5:1, light and dark. */
class PaletteContrastTest {
    private val minimum = 4.5

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** [top] at [alpha] over the opaque [bottom]. */
    private fun over(top: Color, alpha: Float, bottom: Color): Color {
        val t = top.toArgb()
        val b = bottom.toArgb()
        fun mix(shift: Int): Int {
            val tc = (t shr shift) and 0xFF
            val bc = (b shr shift) and 0xFF
            return (tc * alpha + bc * (1 - alpha)).toInt().coerceIn(0, 255)
        }
        return Color(red = mix(16), green = mix(8), blue = mix(0))
    }

    private fun pairs(p: Palette): Map<String, Double> = mapOf(
        "ink on page" to ratio(p.ink, p.page),
        "muted on page" to ratio(p.muted, p.page),
        "page on ink (a selected toggle or button)" to ratio(p.page, p.ink),
        "cloth ink on cloth" to ratio(p.clothInk, p.cloth),
        "cloth ink on the darkest wet patch" to
            ratio(p.clothInk, over(WetPatchColor, WET_PATCH_DARKEST_ALPHA, p.cloth)),
        "will dry ink on its sky" to ratio(p.willDrySky.ink, p.willDrySky.sky),
        "partly dry ink on its sky" to ratio(p.partlyDrySky.ink, p.partlyDrySky.sky),
        "don't bother ink on its sky" to ratio(p.dontBotherSky.ink, p.dontBotherSky.sky),
    )

    private fun check(name: String, p: Palette) {
        for ((pair, value) in pairs(p)) {
            assertTrue(value >= minimum, "$name: $pair is $value, below $minimum")
        }
    }

    @Test
    fun lightPairsAreReadable() = check("light", Palette.light)

    @Test
    fun darkPairsAreReadable() = check("dark", Palette.dark)

    @Test
    fun clothInkIsReadableOnTheDarkestWetPatchOnBothCloths() {
        for (cloth in listOf(Palette.light.cloth, Palette.dark.cloth)) {
            val patch = over(WetPatchColor, WET_PATCH_DARKEST_ALPHA, cloth)
            assertTrue(ratio(Palette.light.clothInk, patch) >= minimum, "cloth ink on a wet patch over $cloth")
        }
    }
}
