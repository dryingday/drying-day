package io.github.dryingday

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point for the future iosApp Xcode project. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
