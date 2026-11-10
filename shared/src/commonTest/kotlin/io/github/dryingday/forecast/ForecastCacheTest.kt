package io.github.dryingday.forecast

import com.russhwolf.settings.MapSettings
import io.github.dryingday.engine.W
import io.github.dryingday.engine.auckland
import io.github.dryingday.engine.forecast
import io.github.dryingday.engine.london
import io.github.dryingday.weather.Coordinates
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val KEY = "forecasts"

private val here = Coordinates(30.12, -40.34)
private val there = Coordinates(-40.56, -130.78)
private val fetched = Instant.parse("2025-06-14T09:40:00Z")

// Three days across the clocks going forward in London, with every optional field set on some hours.
private val springForward = forecast(london, LocalDate(2025, 3, 29)) { _, hour ->
    if (hour % 2 == 0) W(visibilityM = 9000.0, rainChancePercent = 30.0) else W()
}
private val sunny = forecast(auckland, LocalDate(2025, 6, 14))

private fun parse(text: String) = Json.parseToJsonElement(text).jsonObject

class ForecastCacheTest {
    private val settings = MapSettings()
    private val cache = ForecastCache(settings)

    private fun stored() = checkNotNull(settings.getStringOrNull(KEY))

    private fun reloaded() = ForecastCache(settings)

    @Test
    fun startsEmpty() {
        assertNull(cache.get(here))
        assertNull(settings.getStringOrNull(KEY))
    }

    @Test
    fun aForecastRoundTripsEqualToTheOriginal() {
        cache.put(here, fetched, springForward)

        val back = assertNotNull(reloaded().get(here))
        assertEquals(springForward, back.forecast)
        assertEquals(fetched, back.fetchedAt)
        assertEquals("Europe/London", back.forecast.timeZone.id)
        assertTrue(back.forecast.hours.any { it.visibilityM == null })
        assertTrue(back.forecast.hours.any { it.precipitationProbabilityPercent == null })
    }

    @Test
    fun coordinatesAreRoundedOnTheWayIn() {
        cache.put(Coordinates(30.1234, -40.3412), fetched, sunny)

        assertEquals(sunny, cache.get(here)?.forecast)
        assertFalse("30.1234" in stored() || "40.3412" in stored())
    }

    @Test
    fun aNewerFetchReplacesTheEntryForTheSameCoordinates() {
        val later = Instant.parse("2025-06-14T10:45:00Z")
        cache.put(here, fetched, sunny)
        cache.put(here, later, springForward)

        val back = assertNotNull(reloaded().get(here))
        assertEquals(later, back.fetchedAt)
        assertEquals(springForward, back.forecast)
        assertEquals(1, parse(stored()).getValue("entries").jsonArray.size)
    }

    @Test
    fun eachSetOfCoordinatesKeepsItsOwnForecast() {
        cache.put(here, fetched, sunny)
        cache.put(there, fetched, springForward)

        assertEquals(sunny, reloaded().get(here)?.forecast)
        assertEquals(springForward, reloaded().get(there)?.forecast)
    }

    @Test
    fun retainDropsEntriesNoSavedPlaceHas() {
        cache.put(here, fetched, sunny)
        cache.put(there, fetched, springForward)

        cache.retain(setOf(there))

        assertNull(cache.get(here))
        assertNotNull(cache.get(there))
        assertNull(reloaded().get(here))
        assertNotNull(reloaded().get(there))
    }

    @Test
    fun retainWithNoPlacesEmptiesTheCache() {
        cache.put(here, fetched, sunny)

        cache.retain(emptySet())

        assertNull(cache.get(here))
        assertNull(reloaded().get(here))
        assertEquals(0, parse(stored()).getValue("entries").jsonArray.size)
    }

    @Test
    fun aDeviceAndASearchedPlaceWithTheSameCoordinatesShareOneEntryThatOutlivesEither() {
        // The device place and the searched place both round to `here`; removing one leaves the other.
        cache.put(here, fetched, sunny)

        cache.retain(setOf(Coordinates(30.1234, -40.3412)))

        assertNotNull(reloaded().get(here))
    }

    @Test
    fun retainWithNothingToDropDoesNotWrite() {
        cache.put(here, fetched, sunny)
        val before = stored()
        settings.putString(KEY, before + " ")

        cache.retain(setOf(here))

        assertEquals(before + " ", stored())
    }

    @Test
    fun theStoredKeysAreExactlyTheFieldsTheSpecLists() {
        cache.put(here, fetched, springForward)

        val root = parse(stored())
        assertEquals(setOf("version", "entries"), root.keys)
        val entry = root.getValue("entries").jsonArray.single().jsonObject
        assertEquals(setOf("latitude", "longitude", "fetchedAt", "zone", "hours", "days"), entry.keys)
        assertEquals(
            setOf(
                "start",
                "temperatureC",
                "relativeHumidityPercent",
                "windSpeedKmh",
                "visibilityM",
                "precipitationProbabilityPercent",
                "precipitationMm",
                "windGustKmh",
                "shortwaveRadiationWm2",
            ),
            entry.getValue("hours").jsonArray.first().jsonObject.keys,
        )
        assertEquals(setOf("date", "sunrise", "sunset"), entry.getValue("days").jsonArray.first().jsonObject.keys)
    }

    @Test
    fun theTextOfACachedForecastHidesWhatItHolds() {
        cache.put(here, fetched, sunny)

        assertEquals("CachedForecast(hidden)", cache.get(here).toString())
    }

    @Test
    fun corruptJsonIsNoCache() {
        for (text in listOf("not json", "{", "[]", """{"version":1}""", """{"version":1,"entries":3}""")) {
            assertNull(ForecastCache(MapSettings(KEY to text)).get(here), text)
        }
    }

    @Test
    fun anotherVersionIsNoCache() {
        cache.put(here, fetched, sunny)
        val other = stored().replace("\"version\":1", "\"version\":2")

        assertNull(ForecastCache(MapSettings(KEY to other)).get(here))
    }

    @Test
    fun anUnknownZoneDropsOnlyItsOwnEntry() {
        cache.put(here, fetched, sunny)
        cache.put(there, fetched, springForward)

        val broken = stored().replace("Pacific/Auckland", "Nowhere/Atlantis")

        assertTrue("Nowhere/Atlantis" in broken)
        val back = ForecastCache(MapSettings(KEY to broken))
        assertNull(back.get(here))
        assertEquals(springForward, back.get(there)?.forecast)
    }

    @Test
    fun anInvalidHourDropsOnlyItsOwnEntry() {
        cache.put(here, fetched, sunny)
        cache.put(there, fetched, springForward)
        val root = parse(stored())
        val entries = root.getValue("entries").jsonArray.map { it.jsonObject }
        val first = entries.first().toMutableMap()
        val hours = first.getValue("hours").jsonArray.toMutableList()
        hours[0] = JsonObject(hours[0].jsonObject + ("relativeHumidityPercent" to Json.parseToJsonElement("150")))
        first["hours"] = JsonArray(hours)
        val tampered = JsonObject(root + ("entries" to JsonArray(listOf(JsonObject(first)) + entries.drop(1))))

        val back = ForecastCache(MapSettings(KEY to tampered.toString()))

        assertNull(back.get(here))
        assertEquals(springForward, back.get(there)?.forecast)
    }

    @Test
    fun anEntryWithOutOfRangeCoordinatesIsDropped() {
        cache.put(here, fetched, sunny)
        val bad = stored().replace("30.12", "300.12")

        assertNull(ForecastCache(MapSettings(KEY to bad)).get(here))
    }
}
