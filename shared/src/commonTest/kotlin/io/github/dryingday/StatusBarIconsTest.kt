package io.github.dryingday

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusBarIconsTest {
    @Test
    fun theLightSkiesWithDarkInkGetDarkIconsAndTheDontBotherSkyLightOnes() {
        assertEquals(true, darkStatusBarIcons(Palette.light.willDrySky.ink))
        assertEquals(true, darkStatusBarIcons(Palette.light.partlyDrySky.ink))
        assertEquals(false, darkStatusBarIcons(Palette.light.dontBotherSky.ink))
    }

    @Test
    fun everyDarkSkyGetsLightIcons() {
        for (sky in listOf(Palette.dark.willDrySky, Palette.dark.partlyDrySky, Palette.dark.dontBotherSky)) {
            assertEquals(false, darkStatusBarIcons(sky.ink))
        }
    }

    @Test
    fun theOtherScreensFollowThePage() {
        assertEquals(true, darkStatusBarIcons(Palette.light.ink))
        assertEquals(false, darkStatusBarIcons(Palette.dark.ink))
    }
}
