package io.github.dryingday.forecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dryingday.Palette
import io.github.dryingday.bodyStyle
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.forecast_updated
import io.github.dryingday.shared.resources.forecast_updated_today
import io.github.dryingday.shared.resources.forecast_updated_weekday
import io.github.dryingday.shared.resources.forecast_updated_yesterday
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun updatedText(label: UpdatedLabel): String = when (label.day) {
    UpdatedDay.TODAY -> stringResource(Res.string.forecast_updated_today, label.time, label.zone)

    UpdatedDay.YESTERDAY -> stringResource(Res.string.forecast_updated_yesterday, label.time, label.zone)

    UpdatedDay.WEEKDAY -> stringResource(
        Res.string.forecast_updated_weekday,
        stringResource(requireNotNull(label.weekday).text()),
        label.time,
        label.zone,
    )

    UpdatedDay.UNKNOWN -> stringResource(Res.string.forecast_updated, label.time, label.zone)
}

private const val NOTICE_MILLIS = 6_000L

/**
 * A short message at the bottom of the screen that goes by itself, announced to screen readers when it appears.
 * Foundation only: Material's snackbar would need a new dependency. Keyed by [notice]'s id, so a second failure
 * restarts the wait.
 */
@Composable
internal fun RefreshNoticeBar(notice: RefreshNotice, palette: Palette, onDone: () -> Unit, modifier: Modifier) {
    LaunchedEffect(notice.id) {
        delay(NOTICE_MILLIS)
        onDone()
    }
    Box(
        modifier.fillMaxWidth().padding(16.dp).background(palette.ink)
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(16.dp),
    ) {
        BasicText(
            stringResource(notice.error.textUnderForecast()),
            style = bodyStyle().copy(color = palette.page),
        )
    }
}
