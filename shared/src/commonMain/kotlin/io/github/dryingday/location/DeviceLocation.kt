package io.github.dryingday.location

import io.github.dryingday.weather.Coordinates

/** One approximate fix of where the device is, asked for only when the user taps. */
interface DeviceLocation {
    /** Asks for permission if needed, then for one fix. */
    suspend fun locate(): DeviceLocationResult

    /** Opens this app's page in the system settings. Does nothing if the device has no such screen. */
    fun openAppSettings()
}

/** How [DeviceLocation.locate] ended. There is no text, so nothing here can carry coordinates into a message. */
sealed interface DeviceLocationResult {
    data class Found(val coordinates: Coordinates) : DeviceLocationResult

    data object PermissionDenied : DeviceLocationResult

    data object LocationOff : DeviceLocationResult

    data object NoFix : DeviceLocationResult

    /** The app went to the background during the fix. */
    data object Interrupted : DeviceLocationResult
}
