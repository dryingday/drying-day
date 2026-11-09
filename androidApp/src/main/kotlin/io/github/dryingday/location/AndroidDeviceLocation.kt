package io.github.dryingday.location

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.dryingday.weather.Coordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One fix from the platform [LocationManager], and nothing else: no Play Services, no geocoding, no logging.
 * Built by the activity, which owns the permission launcher ([requestPermission]) and the [lifecycle].
 * Everything runs on the main thread.
 */
class AndroidDeviceLocation(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val requestPermission: suspend () -> Boolean,
) : DeviceLocation {
    // Lazy: the activity builds this before it is attached to a Context, when getSystemService would fail.
    private val manager by lazy { context.getSystemService(LocationManager::class.java) }

    override suspend fun locate(): DeviceLocationResult = withContext(Dispatchers.Main) {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        when {
            granted != PackageManager.PERMISSION_GRANTED && !requestPermission() ->
                DeviceLocationResult.PermissionDenied

            !isLocationOn() -> DeviceLocationResult.LocationOff

            else -> fixWhileStarted()
        }
    }

    override fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No settings screen on this device.
        }
    }

    @Suppress("DEPRECATION")
    private fun isLocationOn(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.isLocationEnabled
    } else {
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF,
        ) !=
            Settings.Secure.LOCATION_MODE_OFF
    }

    /** Runs the fix, but gives up with [DeviceLocationResult.Interrupted] if the activity stops meanwhile. */
    private suspend fun fixWhileStarted(): DeviceLocationResult = coroutineScope {
        if (!lifecycle.currentState.isAtLeast(
                Lifecycle.State.STARTED,
            )
        ) {
            return@coroutineScope DeviceLocationResult.Interrupted
        }
        val stopped = CompletableDeferred<Unit>()
        val observer = LifecycleEventObserver { _, event ->
            if (event ==
                Lifecycle.Event.ON_STOP
            ) {
                stopped.complete(Unit)
            }
        }
        lifecycle.addObserver(observer)
        val fix = async { fixOrLastKnown() }
        try {
            select<DeviceLocationResult> {
                stopped.onAwait { DeviceLocationResult.Interrupted }
                fix.onAwait { it }
            }
        } finally {
            lifecycle.removeObserver(observer)
            fix.cancel()
        }
    }

    private suspend fun fixOrLastKnown(): DeviceLocationResult {
        val providers = providersToAsk(manager.getProviders(true))
        val coordinates = firstFix(providers) ?: lastKnown(providers)
        return if (coordinates != null) DeviceLocationResult.Found(coordinates) else DeviceLocationResult.NoFix
    }

    /** Asks every provider at once. The first usable answer wins and every request is cancelled when this ends. */
    private suspend fun firstFix(providers: List<String>): Coordinates? {
        if (providers.isEmpty()) return null
        val cancels = mutableListOf<() -> Unit>()
        try {
            return withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    var waiting = providers.size
                    val answer = { location: Location? ->
                        val coordinates = location?.let(::coordinatesOf)
                        waiting--
                        if (continuation.isActive &&
                            (coordinates != null || waiting == 0)
                        ) {
                            continuation.resume(coordinates)
                        }
                    }
                    for (provider in providers) {
                        if (!continuation.isActive) break
                        // Before API 30 a listener can report its provider disabled more than once.
                        var answered = false
                        cancels += request(provider) { location ->
                            if (!answered) {
                                answered = true
                                answer(location)
                            }
                        }
                    }
                }
            }
        } finally {
            cancels.forEach { it() }
        }
    }

    /**
     * Starts one request and returns how to cancel it. [answer] gets the location, or null for no answer,
     * which is also what a provider that refuses the request gives.
     */
    @Suppress("DEPRECATION")
    private fun request(provider: String, answer: (Location?) -> Unit): () -> Unit {
        val noCancel: () -> Unit = {}
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { answer(it) }
                signal::cancel
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = answer(location)

                    override fun onProviderDisabled(provider: String) = answer(null)

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                val cancel: () -> Unit = { manager.removeUpdates(listener) }
                cancel
            }
        } catch (_: SecurityException) {
            answer(null)
            noCancel
        } catch (_: IllegalArgumentException) {
            answer(null)
            noCancel
        }
    }

    private fun lastKnown(providers: List<String>): Coordinates? {
        val candidates = providers.mapNotNull { provider ->
            val location = try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            // The clock is read after the location, so a fix that has just landed can't look like it's from the future.
            val nowMillis = SystemClock.elapsedRealtimeNanos() / NANOS_PER_MILLI
            val coordinates = location?.let(::coordinatesOf)
            coordinates?.let { it to nowMillis - location.elapsedRealtimeNanos / NANOS_PER_MILLI }
        }
        return newestRecent(candidates)
    }

    /** Null for a location Coordinates won't accept. */
    private fun coordinatesOf(location: Location): Coordinates? = try {
        Coordinates(location.latitude, location.longitude)
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
