package io.github.dryingday

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import io.github.dryingday.location.AndroidDeviceLocation
import io.github.dryingday.location.DeviceLocation
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var permissionAnswer: CompletableDeferred<Boolean>? = null

    // Registered before the activity starts, as the launcher requires. The answer is false when the system
    // declined to show the dialog too.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionAnswer?.complete(it) }

    /** Nothing asks for a location until the first-run screen does. */
    internal val deviceLocation: DeviceLocation = AndroidDeviceLocation(this, lifecycle, ::requestCoarseLocation)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    private suspend fun requestCoarseLocation(): Boolean {
        val answer = CompletableDeferred<Boolean>()
        permissionAnswer?.complete(false)
        permissionAnswer = answer
        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        try {
            return answer.await()
        } finally {
            if (permissionAnswer === answer) permissionAnswer = null
        }
    }
}
