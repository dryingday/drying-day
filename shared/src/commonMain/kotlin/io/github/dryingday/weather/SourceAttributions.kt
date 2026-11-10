package io.github.dryingday.weather

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.attribution_places
import io.github.dryingday.shared.resources.attribution_weather
import org.jetbrains.compose.resources.stringResource

private val linkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

/**
 * The credits a source's licence asks for, with links to the source and the licence. Keep it next to the data.
 * A tap goes to the browser through [LinkAnnotation.Url], which does nothing if the device has no browser.
 */
@Composable
fun SourceAttributions(
    attributions: List<Attribution>,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Column(modifier) {
        for (attribution in attributions) {
            val sentence =
                when (attribution.covers) {
                    AttributionScope.WEATHER -> Res.string.attribution_weather
                    AttributionScope.PLACES -> Res.string.attribution_places
                }
            BasicText(
                attributionText(stringResource(sentence, attribution.name, attribution.licence), attribution),
                style = TextStyle(color = color),
            )
        }
    }
}

/** [sentence], with the attribution's name and licence linked wherever a translation has put them. */
internal fun attributionText(sentence: String, attribution: Attribution): AnnotatedString = buildAnnotatedString {
    append(sentence)
    for ((text, url) in listOf(attribution.name to attribution.url, attribution.licence to attribution.licenceUrl)) {
        val start = sentence.indexOf(text)
        if (start >= 0) addLink(LinkAnnotation.Url(url, linkStyles), start, start + text.length)
    }
}
