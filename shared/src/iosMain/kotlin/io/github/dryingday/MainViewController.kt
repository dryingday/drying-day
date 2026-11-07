package io.github.dryingday

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point for the future iosApp Xcode project. Its Info.plist needs
 * `CADisableMinimumFrameDurationOnPhone` set to true, or Compose stops the app at launch.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
