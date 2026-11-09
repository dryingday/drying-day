package io.github.dryingday

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.NSUserDefaultsSettings
import io.github.dryingday.places.PlaceStore
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/**
 * Entry point for the future iosApp Xcode project. Its Info.plist needs
 * `CADisableMinimumFrameDurationOnPhone` set to true, or Compose stops the app at launch.
 * There is no device location on iOS yet, so the first-run screen offers search only.
 */
fun mainViewController(): UIViewController = ComposeUIViewController {
    App(PlaceStore(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)), deviceLocation = null)
}
