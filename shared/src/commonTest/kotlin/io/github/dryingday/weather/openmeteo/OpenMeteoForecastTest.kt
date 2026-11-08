package io.github.dryingday.weather.openmeteo

import io.github.dryingday.engine.Forecast
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.SourceError
import io.github.dryingday.weather.SourceResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val FIRST_STAMP = 1749906000L
private const val SECOND_STAMP = 1749909600L
private const val GOOD_BODY =
    """{"timezone":"Etc/UTC","utc_offset_seconds":0,
    "hourly":{"time":[$FIRST_STAMP,$SECOND_STAMP],"temperature_2m":[20.0,20.0],"relative_humidity_2m":[50,50],
    "wind_speed_10m":[10.0,10.0],"visibility":[20000.0,20000.0],"precipitation_probability":[0,0],
    "precipitation":[0.0,0.0],"wind_gusts_10m":[20.0,20.0],"shortwave_radiation":[400.0,400.0]},
    "daily":{"time":[1749859200],"sunrise":[1749880000],"sunset":[1749940000],"daylight_duration":[60000.0]}}"""

private val somewhere = Coordinates(12.3456, -65.4321)

private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    OpenMeteoSource(MockEngine(handler))

private fun failure(error: SourceError) = SourceResult.Failure(error)

class OpenMeteoForecastTest {
    @Test
    fun asksForExactlyTheSpecsUrl() = runTest {
        var url = ""
        source {
            url = it.url.toString()
            respond(GOOD_BODY)
        }.forecast(somewhere)

        val hourly =
            "temperature_2m%2Crelative_humidity_2m%2Cwind_speed_10m%2Cvisibility%2Cprecipitation_probability%2C" +
                "precipitation%2Cwind_gusts_10m%2Cshortwave_radiation"
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=12.35&longitude=-65.43&hourly=$hourly" +
                "&daily=sunrise%2Csunset%2Cdaylight_duration&timezone=auto&timeformat=unixtime" +
                "&past_days=1&forecast_days=5",
            url,
        )
    }

    @Test
    fun roundsCoordinatesToTwoDecimals() {
        assertEquals(Coordinates(12.35, -65.43), roundedForRequest(somewhere))
        assertEquals(Coordinates(90.0, 180.0), roundedForRequest(Coordinates(89.999, 179.999)))
    }

    @Test
    fun aValueRoundingToZeroIsNotNegativeZero() {
        val rounded = roundedForRequest(Coordinates(-0.001, -0.004))

        assertEquals("0.0", rounded.latitude.toString())
        assertEquals("0.0", rounded.longitude.toString())
    }

    @Test
    fun setsTheTimeoutsOnTheRequest() = runTest {
        var connect: Long? = null
        var whole: Long? = null
        source {
            val timeout = it.getCapabilityOrNull(HttpTimeoutCapability)
            connect = timeout?.connectTimeoutMillis
            whole = timeout?.requestTimeoutMillis
            respond(GOOD_BODY)
        }.forecast(somewhere)

        assertEquals(10_000, connect)
        assertEquals(30_000, whole)
    }

    @Test
    fun returnsTheMappedForecast() = runTest {
        val result = source { respond(GOOD_BODY) }.forecast(somewhere)

        val forecast: Forecast = assertIs<SourceResult.Success<Forecast>>(result).value
        assertEquals(FIRST_STAMP, forecast.hours.single().start.epochSeconds)
    }

    @Test
    fun aNon2xxIsService() = runTest {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.TooManyRequests, HttpStatusCode.BadGateway)) {
            assertEquals(failure(SourceError.SERVICE), source { respond("", status) }.forecast(somewhere), "$status")
        }
    }

    @Test
    fun aRedirectIsServiceAndIsNotFollowed() = runTest {
        var requests = 0
        val result =
            source {
                requests++
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://elsewhere.example/"))
            }.forecast(somewhere)

        assertEquals(failure(SourceError.SERVICE), result)
        assertEquals(1, requests)
    }

    @Test
    fun an2xxThatCannotBeReadIsBadResponse() = runTest {
        assertEquals(failure(SourceError.BAD_RESPONSE), source { respond("not json") }.forecast(somewhere))
    }

    @Test
    fun aFailedConnectionIsUnreachable() = runTest {
        assertEquals(failure(SourceError.UNREACHABLE), source { throw IOException("no route") }.forecast(somewhere))
    }

    @Test
    fun aConnectionLostWhileReadingTheBodyIsUnreachable() = runTest {
        val result =
            source {
                val body = ByteChannel(autoFlush = true)
                body.writeStringUtf8("{")
                body.close(IOException("connection lost"))
                respond(body, HttpStatusCode.OK)
            }.forecast(somewhere)

        assertEquals(failure(SourceError.UNREACHABLE), result)
    }

    @Test
    fun anyOtherExceptionIsUnreachable() = runTest {
        val result = source { error("not an I/O problem") }.forecast(somewhere)

        assertEquals(failure(SourceError.UNREACHABLE), result)
    }

    @Test
    fun aFailureDoesNotCarryTheCoordinates() = runTest {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=12.35&longitude=-65.43"

        val result = source { throw IOException("failed: $url") }.forecast(somewhere)

        assertFalse(result.toString().contains("12.35"), "$result")
        assertFalse(result.toString().contains("65.43"), "$result")
    }

    @Test
    fun cancellingTheCallerCancelsTheCall() = runTest {
        val started = CompletableDeferred<Unit>()
        var returned = false
        val caller =
            launch(start = CoroutineStart.UNDISPATCHED) {
                source {
                    started.complete(Unit)
                    awaitCancellation()
                }.forecast(somewhere)
                returned = true
            }
        started.await()

        caller.cancelAndJoin()

        assertTrue(caller.isCancelled)
        assertFalse(returned)
    }

    @Test
    fun aCancellationThatIsNotTheCallersIsUnreachable() = runTest {
        val result = source { throw CancellationException("the engine gave up") }.forecast(somewhere)

        assertEquals(failure(SourceError.UNREACHABLE), result)
    }
}
