package io.github.dryingday

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.app_name
import io.github.dryingday.weather.SourceAttributions
import io.github.dryingday.weather.openmeteo.openMeteoAttributions
import org.jetbrains.compose.resources.stringResource

/** Placeholder screen until the main screen arrives in bolt 5. */
@Composable
fun App() {
    Box(modifier = Modifier.fillMaxSize()) {
        BasicText(stringResource(Res.string.app_name), Modifier.align(Alignment.Center))
        SourceAttributions(
            openMeteoAttributions,
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
        )
    }
}
