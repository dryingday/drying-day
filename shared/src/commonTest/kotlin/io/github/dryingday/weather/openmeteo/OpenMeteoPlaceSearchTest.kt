package io.github.dryingday.weather.openmeteo

import io.github.dryingday.weather.AttributionScope
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import io.github.dryingday.weather.SourceError
import io.github.dryingday.weather.SourceResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Invented places, in the shape the live API returns.
private const val TWO_PLACES =
    """{"results":[
    {"id":1,"name":"Testville","latitude":12.34567,"longitude":-65.43219,"elevation":28.0,"feature_code":"PPL",
    "country_code":"XX","admin1_id":2,"timezone":"Etc/UTC","population":100,"postcodes":["0000"],
    "country":"Exampleland","admin1":"Sample Region","admin2":"Sample County"},
    {"id":2,"name":"Otherton","latitude":-1.5,"longitude":2.5}
    ],"generationtime_ms":0.1}"""

private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    OpenMeteoSource(MockEngine(handler))

private fun success(vararg places: Place) = SourceResult.Success(places.toList())

private fun failure(error: SourceError) = SourceResult.Failure(error)

private suspend fun placesFrom(body: String) = source { respond(body) }.searchPlaces("Testville", null)

class OpenMeteoPlaceSearchTest {
    @Test
    fun asksForTheSpecsUrlWithALanguage() = runTest {
        var url = ""
        source {
            url = it.url.toString()
            respond(TWO_PLACES)
        }.searchPlaces("Testville", "de")

        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=Testville&count=10&format=json&language=de",
            url,
        )
    }

    @Test
    fun leavesOutLanguageWhenThereIsNone() = runTest {
        var url = ""
        source {
            url = it.url.toString()
            respond(TWO_PLACES)
        }.searchPlaces("Testville", null)

        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Testville&count=10&format=json", url)
    }

    @Test
    fun sendsTheTrimmedQueryEncoded() = runTest {
        var name: String? = null
        var url = ""
        source {
            name = it.url.parameters["name"]
            url = it.url.toString()
            respond(TWO_PLACES)
        }.searchPlaces("  Test Ville & Co=1 ", null)

        assertEquals("Test Ville & Co=1", name)
        assertTrue(url.contains("name=Test+Ville+%26+Co%3D1&"), url)
    }

    @Test
    fun aQueryUnderTwoCharactersMakesNoRequest() = runTest {
        var requests = 0
        val searching =
            source {
                requests++
                respond(TWO_PLACES)
            }

        for (query in listOf("", " ", "T", "  T  ")) {
            assertEquals(success(), searching.searchPlaces(query, null), "'$query'")
        }
        assertEquals(0, requests)
    }

    @Test
    fun aTwoCharacterQueryIsSent() = runTest {
        var requests = 0
        source {
            requests++
            respond(TWO_PLACES)
        }.searchPlaces(" Te ", null)

        assertEquals(1, requests)
    }

    @Test
    fun mapsPlacesWithOptionalRegionAndCountry() = runTest {
        assertEquals(
            success(
                Place("Testville", "Sample Region", "Exampleland", Coordinates(12.34567, -65.43219)),
                Place("Otherton", null, null, Coordinates(-1.5, 2.5)),
            ),
            placesFrom(TWO_PLACES),
        )
    }

    @Test
    fun noResultsKeyIsAnEmptyList() = runTest {
        assertEquals(success(), placesFrom("""{"generationtime_ms":0.1}"""))
    }

    @Test
    fun nullResultsIsAnEmptyList() = runTest {
        assertEquals(success(), placesFrom("""{"results":null}"""))
    }

    @Test
    fun skipsResultsMissingANameOrCoordinates() = runTest {
        val body =
            """{"results":[
            {"latitude":1.0,"longitude":2.0},
            {"name":"  ","latitude":1.0,"longitude":2.0},
            {"name":null,"latitude":1.0,"longitude":2.0},
            {"name":"No latitude","longitude":2.0},
            {"name":"No longitude","latitude":1.0},
            {"name":"Null latitude","latitude":null,"longitude":2.0},
            {"name":"Otherton","latitude":-1.5,"longitude":2.5}
            ]}"""

        assertEquals(success(Place("Otherton", null, null, Coordinates(-1.5, 2.5))), placesFrom(body))
    }

    @Test
    fun skipsResultsWithCoordinatesOutOfRange() = runTest {
        val body =
            """{"results":[
            {"name":"Too far north","latitude":91.0,"longitude":2.0},
            {"name":"Too far east","latitude":1.0,"longitude":181.0},
            {"name":"Otherton","latitude":-1.5,"longitude":2.5}
            ]}"""

        assertEquals(success(Place("Otherton", null, null, Coordinates(-1.5, 2.5))), placesFrom(body))
    }

    @Test
    fun aBodyThatIsNotTheExpectedJsonIsBadResponse() = runTest {
        assertEquals(failure(SourceError.BAD_RESPONSE), placesFrom("not json"))
        assertEquals(failure(SourceError.BAD_RESPONSE), placesFrom("""{"results":"nope"}"""))
    }

    @Test
    fun aNon2xxIsService() = runTest {
        val result = source { respond("""{"error":true}""", HttpStatusCode.BadRequest) }.searchPlaces("Testville", null)

        assertEquals(failure(SourceError.SERVICE), result)
    }

    @Test
    fun aFailedConnectionIsUnreachable() = runTest {
        val result = source { throw IOException("no route") }.searchPlaces("Testville", null)

        assertEquals(failure(SourceError.UNREACHABLE), result)
    }

    @Test
    fun creditsBothTheWeatherAndThePlaces() {
        val source = source { respond("") }

        assertEquals(
            setOf(AttributionScope.WEATHER, AttributionScope.PLACES),
            source.attributions.map { it.covers }.toSet(),
        )
        assertTrue(source.attributions.all { it.url.startsWith("https://") && it.licenceUrl.startsWith("https://") })
    }
}
