package io.github.dryingday

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A sky and the ink that text on it uses. */
class Sky(val sky: Color, val ink: Color)

/**
 * The one palette of docs/bolts/09-washing-line.md, "Colour". [Palette.light] and [Palette.dark] are the two sets;
 * [palette] picks the one the system is showing. The skies are tokens here: which one a day gets is chosen elsewhere.
 */
@Suppress("LongParameterList") // One named colour each, as the spec's table has them.
class Palette(
    val page: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val laundryBlue: Color,
    val secondPrint: Color,
    val cloth: Color,
    val clothInk: Color,
    val garmentOutline: Color,
    val pegWood: Color,
    val sun: Color,
    val sunRing: Color,
    val cloud: Color,
    val darkCloud: Color,
    val drop: Color,
    val dropEdge: Color,
    val willDrySky: Sky,
    val partlyDrySky: Sky,
    val dontBotherSky: Sky,
) {
    companion object {
        val light = Palette(
            page = Color(0xFFF3F6F9),
            ink = Color(0xFF16202B),
            muted = Color(0xFF4A5666),
            rule = Color(0xFFC3CDD7),
            laundryBlue = Color(0xFF2F5F8A),
            secondPrint = SecondPrint,
            cloth = Color(0xFFFFFFFF),
            clothInk = Color(0xFF16202B),
            garmentOutline = Color(0xFF16202B),
            pegWood = Color(0xFFC99A5B),
            sun = Color(0xFFFFD84D),
            sunRing = Color.White.copy(alpha = 0.30f),
            cloud = Color(0xFFEEF4F9),
            darkCloud = Color(0xFF2F3C4A),
            drop = Color(0xFFDDF0FC),
            dropEdge = Color(0xFF2F5F8A),
            willDrySky = Sky(Color(0xFF62B3EA), Color(0xFF0E1A28)),
            partlyDrySky = Sky(Color(0xFFA9BCCB), Color(0xFF0E1A28)),
            dontBotherSky = Sky(Color(0xFF4A5B6C), Color(0xFFF4F7FA)),
        )

        val dark = Palette(
            page = Color(0xFF14171A),
            ink = Color(0xFFE8EAED),
            muted = Color(0xFFA3ABB6),
            rule = Color(0xFF33383F),
            laundryBlue = Color(0xFF9CC2E3),
            secondPrint = SecondPrint,
            cloth = Color(0xFFECE8DF),
            clothInk = Color(0xFF16202B),
            garmentOutline = Color(0xFF7D8794),
            pegWood = Color(0xFFB9893F),
            sun = Color(0xFFF4C84A),
            sunRing = Color.White.copy(alpha = 0.18f),
            cloud = Color(0xFF6D7C8C),
            darkCloud = Color(0xFF56677A),
            drop = Color(0xFFCFE8F8),
            dropEdge = Color(0xFF0E1A28),
            willDrySky = Sky(Color(0xFF1F5FA8), Color(0xFFF4F7FA)),
            partlyDrySky = Sky(Color(0xFF435263), Color(0xFFF4F7FA)),
            dontBotherSky = Sky(Color(0xFF151C25), Color(0xFFF4F7FA)),
        )
    }
}

/** The verdict's second print: the light mode's laundry blue at 30 % in both modes, as the cloth is light in both. */
private val SecondPrint = Color(0xFF2F5F8A).copy(alpha = 0.30f)

/** The wet patch on a cloth, in both modes. */
val WetPatchColor = Color(0xFF1C3A5C)

/** The wet patch's opacity on a cloth at its darkest, and at its lightest. */
const val WET_PATCH_DARKEST_ALPHA = 0.40f
const val WET_PATCH_LIGHTEST_ALPHA = 0.16f

/** The row of washing, the same in light and dark. */
val GarmentColors = listOf(Color(0xFFFFFFFF), Color(0xFF9CC2E3), Color(0xFFE8D5B0), Color(0xFFF2B8A2))

/** The palette for the system's current light or dark setting. */
@Composable
fun palette(): Palette = if (isSystemInDarkTheme()) Palette.dark else Palette.light
