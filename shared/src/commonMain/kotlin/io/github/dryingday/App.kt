package io.github.dryingday

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.dryingday.firstrun.FirstRunScreen
import io.github.dryingday.firstrun.FirstRunState
import io.github.dryingday.location.DeviceLocation
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.app_name
import io.github.dryingday.weather.SourceAttributions
import io.github.dryingday.weather.openmeteo.openMeteoAttributions
import org.jetbrains.compose.resources.stringResource

/**
 * First run while there is no current place, otherwise the placeholder screen until the main screen arrives in
 * bolt 5. [deviceLocation] is null where the device can't be asked.
 */
@Composable
fun App(placeStore: PlaceStore, deviceLocation: DeviceLocation?) {
    var hasPlace by remember { mutableStateOf(placeStore.current != null) }
    if (hasPlace) {
        Placeholder()
    } else {
        val state = remember(placeStore, deviceLocation) {
            FirstRunState(deviceLocation, placeStore) { hasPlace = placeStore.current != null }
        }
        // The search screen arrives with the next PR, which replaces this no-op.
        FirstRunScreen(state, onSearch = {})
    }
}

@Composable
private fun Placeholder() {
    Box(modifier = Modifier.fillMaxSize()) {
        BasicText(stringResource(Res.string.app_name), Modifier.align(Alignment.Center))
        SourceAttributions(
            openMeteoAttributions,
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
        )
    }
}
