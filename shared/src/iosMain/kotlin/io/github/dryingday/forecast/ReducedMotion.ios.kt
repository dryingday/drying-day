package io.github.dryingday.forecast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

// Reduce Motion, read again whenever the system says it changed.
@Composable
internal actual fun animationsEnabled(): Boolean {
    var reduce by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(
            UIAccessibilityReduceMotionStatusDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { _ -> reduce = UIAccessibilityIsReduceMotionEnabled() }
        reduce = UIAccessibilityIsReduceMotionEnabled()
        onDispose { center.removeObserver(token) }
    }
    return animationsOnWithReduceMotion(reduce)
}
