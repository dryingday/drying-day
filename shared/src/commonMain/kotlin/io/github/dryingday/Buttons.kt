package io.github.dryingday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The button of the first run, search and places screens: square corners, a 2 dp ink border, filled with ink and
 * the label in the page colour, bold. At least 48 dp high, and it grows with the text.
 */
@Composable
fun InkButton(label: String, palette: Palette, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(palette.ink)
            .border(2.dp, palette.ink)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = buttonStyle().copy(color = palette.page, textAlign = TextAlign.Center))
    }
}
