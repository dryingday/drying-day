package io.github.dryingday

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.NSUserDefaultsSettings
import io.github.dryingday.forecast.twentyFourHourFormat
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.weather.openmeteo.sharedOpenMeteoSource
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController
import kotlin.time.Clock

/**
 * Entry point for the future iosApp Xcode project. Its Info.plist needs
 * `CADisableMinimumFrameDurationOnPhone` set to true, or Compose stops the app at launch.
 * There is no device location on iOS yet, so the first-run screen offers search only. It isn't shipped in v1, so
 * the app is always visible here; an iOS app would pass its own lifecycle in. Times are 24-hour.
 */
fun mainViewController(): UIViewController {
    val defaults = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    val placeStore = PlaceStore(defaults)
    return ComposeUIViewController {
        App(
            placeStore = placeStore,
            searchSource = sharedOpenMeteoSource,
            forecastSource = sharedOpenMeteoSource,
            deviceLocation = null,
            forecastSettings = defaults,
            settings = defaults,
            clock = Clock.System,
            formatTime = twentyFourHourFormat,
            visible = true,
        )
    }
}
