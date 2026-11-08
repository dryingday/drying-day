package io.github.dryingday.weather

import androidx.compose.ui.text.LinkAnnotation
import io.github.dryingday.weather.openmeteo.openMeteoAttributions
import kotlin.test.Test
import kotlin.test.assertEquals

private val attribution =
    Attribution(
        "Sample.org",
        "https://sample.org/",
        "CC BY 4.0",
        "https://example.org/licence",
        AttributionScope.WEATHER,
    )

private fun links(sentence: String): Map<String, String> {
    val text = attributionText(sentence, attribution)
    return text.getLinkAnnotations(0, text.length).associate {
        text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url
    }
}

class SourceAttributionsTest {
    @Test
    fun linksTheNameAndTheLicence() {
        assertEquals(
            mapOf("Sample.org" to "https://sample.org/", "CC BY 4.0" to "https://example.org/licence"),
            links("Weather data by Sample.org (CC BY 4.0)."),
        )
    }

    @Test
    fun linksThemWhereverATranslationPutsThem() {
        assertEquals(
            mapOf("Sample.org" to "https://sample.org/", "CC BY 4.0" to "https://example.org/licence"),
            links("(CC BY 4.0) a Sample.org"),
        )
    }

    @Test
    fun keepsTheSentenceWhole() {
        val sentence = "Weather data by Sample.org (CC BY 4.0)."

        assertEquals(sentence, attributionText(sentence, attribution).text)
    }

    @Test
    fun aSentenceMissingANameStillShowsWithoutThatLink() {
        assertEquals(mapOf("CC BY 4.0" to "https://example.org/licence"), links("Licence: CC BY 4.0"))
    }

    @Test
    fun openMeteoCreditsTheWeatherTheLicenceAndThePlaces() {
        val weather = openMeteoAttributions.single { it.covers == AttributionScope.WEATHER }

        assertEquals("Open-Meteo.com", weather.name)
        assertEquals("https://open-meteo.com/", weather.url)
        assertEquals("CC BY 4.0", weather.licence)
        assertEquals("https://creativecommons.org/licenses/by/4.0/", weather.licenceUrl)
        assertEquals(1, openMeteoAttributions.count { it.covers == AttributionScope.PLACES })
    }
}
