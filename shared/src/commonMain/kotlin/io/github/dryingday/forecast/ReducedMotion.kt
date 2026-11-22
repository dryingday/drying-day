package io.github.dryingday.forecast

import androidx.compose.runtime.Composable

// Whether the system's animations are on. Every movement on the main screen is decorative and follows it.
// See docs/bolts/09-washing-line.md, "Motion".

/**
 * Whether the system's animations are on, observed: changing the setting while the app is open recomposes with
 * the new value. On Android, the animator duration scale ("Remove animations" sets it to 0); on iOS, Reduce Motion.
 */
@Composable
internal expect fun animationsEnabled(): Boolean

/** Android's animator duration scale to on or off: 0 is off, anything else on. */
internal fun animationsOnAtScale(durationScale: Float): Boolean = durationScale != 0f

/** iOS's Reduce Motion to on or off. */
internal fun animationsOnWithReduceMotion(reduceMotion: Boolean): Boolean = !reduceMotion
