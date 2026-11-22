package io.github.dryingday

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val DARK_INK_BELOW = 0.5f

/** Whether the status bar's icons should be dark: when the text under them, [ink], is dark too. */
internal fun darkStatusBarIcons(ink: Color): Boolean = ink.luminance() < DARK_INK_BELOW

/**
 * Tells the platform, through [setDarkIcons], which status bar icons suit a screen whose text under the bar is
 * [ink]: dark icons on a light background, light icons on a dark one. The app draws behind the status bar.
 */
@Composable
internal fun StatusBarIcons(ink: Color, setDarkIcons: (Boolean) -> Unit) {
    val dark = darkStatusBarIcons(ink)
    SideEffect { setDarkIcons(dark) }
}
