package io.github.dryingday

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** The spec's Colour table: the second print, the garments' outline, the dark cloud and the sun ring. */
class PaletteTokensTest {
    @Test
    fun secondPrintIsTheLightBlueAtThirtyPercentInBothModes() {
        val expected = Color(0xFF2F5F8A).copy(alpha = 0.30f)
        assertEquals(expected, Palette.light.secondPrint)
        assertEquals(expected, Palette.dark.secondPrint)
    }

    @Test
    fun garmentOutlineIsMidGreyInDarkMode() {
        assertEquals(Color(0xFF16202B), Palette.light.garmentOutline)
        assertEquals(Color(0xFF7D8794), Palette.dark.garmentOutline)
    }

    @Test
    fun darkCloudIsLighterInDarkMode() {
        assertEquals(Color(0xFF2F3C4A), Palette.light.darkCloud)
        assertEquals(Color(0xFF56677A), Palette.dark.darkCloud)
    }

    @Test
    fun sunRingIsWhiteAtThirtyAndEighteenPercent() {
        assertEquals(Color.White.copy(alpha = 0.30f), Palette.light.sunRing)
        assertEquals(Color.White.copy(alpha = 0.18f), Palette.dark.sunRing)
    }
}
