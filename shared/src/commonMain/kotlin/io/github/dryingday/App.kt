package io.github.dryingday

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.app_name
import org.jetbrains.compose.resources.stringResource

/** Placeholder screen until the main screen arrives in bolt 5. */
@Composable
fun App() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(stringResource(Res.string.app_name))
    }
}
