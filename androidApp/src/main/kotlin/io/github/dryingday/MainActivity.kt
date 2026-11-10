package io.github.dryingday

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.dryingday.location.AndroidDeviceLocation
import io.github.dryingday.location.DeviceLocation
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.weather.openmeteo.sharedOpenMeteoSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.TimeZone
import java.util.Date
import kotlin.time.Clock

class MainActivity : ComponentActivity() {
    private var permissionAnswer: CompletableDeferred<Boolean>? = null

    // Registered before the activity starts, as the launcher requires. The answer is false when the system
    // declined to show the dialog too.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionAnswer?.complete(it) }

    /** Nothing asks for a location until the first-run screen does. */
    internal val deviceLocation: DeviceLocation = AndroidDeviceLocation(this, lifecycle, ::requestCoarseLocation)

    // The app is visible from STARTED to STOPPED, the same lifecycle the device location watches.
    private var appVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Private to the app, and left out of backups by the manifest.
        val placeStore = PlaceStore(SharedPreferencesSettings(getSharedPreferences("places", MODE_PRIVATE)))
        val forecasts = SharedPreferencesSettings(getSharedPreferences("forecasts", MODE_PRIVATE))
        val settings = SharedPreferencesSettings(getSharedPreferences("settings", MODE_PRIVATE))
        appVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycle.addObserver(
            LifecycleEventObserver { _, _ -> appVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
        )
        setContent {
            App(
                placeStore = placeStore,
                searchSource = sharedOpenMeteoSource,
                forecastSource = sharedOpenMeteoSource,
                deviceLocation = deviceLocation,
                forecastSettings = forecasts,
                settings = settings,
                clock = Clock.System,
                formatTime = ::formatTime,
                visible = appVisible,
            ) { enabled, onBack -> BackHandler(enabled, onBack) }
        }
    }

    // In the device's 12- or 24-hour setting, in the forecast's zone and not the device's.
    private fun formatTime(instant: kotlin.time.Instant, zone: TimeZone): String {
        val format = android.text.format.DateFormat.getTimeFormat(this)
        format.timeZone = java.util.TimeZone.getTimeZone(zone.id)
        return format.format(Date(instant.toEpochMilliseconds()))
    }

    private suspend fun requestCoarseLocation(): Boolean {
        val answer = CompletableDeferred<Boolean>()
        permissionAnswer?.complete(false)
        permissionAnswer = answer
        return try {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            answer.await()
        } catch (_: ActivityNotFoundException) {
            false // No permission screen on this device, so the system can't show the dialog.
        } finally {
            if (permissionAnswer === answer) permissionAnswer = null
        }
    }
}
