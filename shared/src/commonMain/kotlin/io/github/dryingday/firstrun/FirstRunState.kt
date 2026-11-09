package io.github.dryingday.firstrun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dryingday.location.DeviceLocation
import io.github.dryingday.location.DeviceLocationResult
import io.github.dryingday.places.PlaceStore

/** Why the last tap of the location button gave no place. Each has its own message on the screen. */
enum class LocationProblem { PermissionDenied, LocationOff, NoFix }

/**
 * What the first-run screen shows and does. [location] is null where the device can't be asked, and the choice
 * isn't offered then. [onPlaceSaved] runs after a fix has been saved as the current place.
 *
 * [useLocation] is called from a button tap, in a coroutine the screen owns, so leaving the screen cancels the
 * fix. Nothing here starts a fix by itself.
 */
class FirstRunState(
    private val location: DeviceLocation?,
    private val places: PlaceStore,
    private val onPlaceSaved: () -> Unit,
) {
    val canUseLocation: Boolean get() = location != null

    /** True from a tap until the fix ends, however it ends. */
    var waiting by mutableStateOf(false)
        private set

    var problem: LocationProblem? by mutableStateOf(null)
        private set

    /** Does nothing while a fix is already running. [DeviceLocationResult.Interrupted] leaves no message. */
    suspend fun useLocation() {
        val device = location ?: return
        if (waiting) return
        waiting = true
        problem = null
        val result = try {
            device.locate()
        } finally {
            waiting = false
        }
        when (result) {
            is DeviceLocationResult.Found -> {
                places.setDevice(result.coordinates)
                onPlaceSaved()
            }

            DeviceLocationResult.PermissionDenied -> problem = LocationProblem.PermissionDenied

            DeviceLocationResult.LocationOff -> problem = LocationProblem.LocationOff

            DeviceLocationResult.NoFix -> problem = LocationProblem.NoFix

            DeviceLocationResult.Interrupted -> Unit
        }
    }

    fun openSettings() {
        location?.openAppSettings()
    }
}
