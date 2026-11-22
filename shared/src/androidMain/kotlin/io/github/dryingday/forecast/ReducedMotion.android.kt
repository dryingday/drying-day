package io.github.dryingday.forecast

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// The animator duration scale, read again whenever it changes.
@Composable
internal actual fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var scale by remember(resolver) { mutableFloatStateOf(durationScale(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = durationScale(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // In case it changed between the first read and registering.
        scale = durationScale(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return animationsOnAtScale(scale)
}

private fun durationScale(resolver: ContentResolver): Float =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
