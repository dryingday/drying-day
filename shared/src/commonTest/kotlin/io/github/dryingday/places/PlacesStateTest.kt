package io.github.dryingday.places

import com.russhwolf.settings.MapSettings
import io.github.dryingday.engine.day0
import io.github.dryingday.engine.forecast
import io.github.dryingday.engine.london
import io.github.dryingday.forecast.ForecastCache
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

private val seaville = Place("Seaville", null, null, Coordinates(30.12, -40.34))
private val deepsea = Place("Deepsea", null, null, Coordinates(-10.5, 150.25))

class PlacesStateTest {
    private val store = PlaceStore(MapSettings())
    private val cache = ForecastCache(MapSettings())
    private val state = PlacesState(store, cache)

    @Test
    fun refreshPicksUpChangesMadeElsewhere() {
        store.addSearched(seaville)
        assertEquals(emptyList(), state.places)
        state.refresh()
        assertEquals(store.places, state.places)
        assertEquals(store.current, state.current)
    }

    @Test
    fun selectMakesCurrent() {
        store.addSearched(seaville)
        store.addSearched(deepsea)
        state.refresh()
        state.select(state.places.first())
        assertEquals(state.places.first(), state.current)
    }

    @Test
    fun removingCurrentFallsBackToFirstThenToNothing() {
        store.addSearched(seaville)
        store.addSearched(deepsea)
        state.refresh()
        state.remove(state.places.last())
        assertEquals(1, state.places.size)
        assertEquals(state.places.first(), state.current)
        state.remove(state.places.first())
        assertEquals(emptyList(), state.places)
        assertNull(state.current)
    }

    @Test
    fun removingAPlaceDropsItsForecastAndKeepsTheOthers() {
        store.addSearched(seaville)
        store.addSearched(deepsea)
        state.refresh()
        for (place in listOf(seaville, deepsea)) {
            cache.put(place.coordinates, Instant.parse("2025-06-14T09:00:00Z"), forecast(london, day0))
        }

        state.remove(state.places.first())

        assertNull(cache.get(seaville.coordinates))
        assertNotNull(cache.get(deepsea.coordinates))
    }

    @Test
    fun refreshAfterAChangeMadeElsewhereDropsForecastsOfPlacesNoLongerSaved() {
        store.addSearched(seaville)
        state.refresh()
        cache.put(seaville.coordinates, Instant.parse("2025-06-14T09:00:00Z"), forecast(london, day0))
        cache.put(deepsea.coordinates, Instant.parse("2025-06-14T09:00:00Z"), forecast(london, day0))

        state.refresh()

        assertNotNull(cache.get(seaville.coordinates))
        assertNull(cache.get(deepsea.coordinates))
    }

    @Test
    fun aDeviceAndASearchedPlaceWithTheSameCoordinatesShareAnEntryThatOutlivesEither() {
        store.addSearched(seaville)
        store.setDevice(seaville.coordinates)
        state.refresh()
        cache.put(seaville.coordinates, Instant.parse("2025-06-14T09:00:00Z"), forecast(london, day0))

        state.remove(state.places.first { it is SavedPlace.Device })

        assertNotNull(cache.get(seaville.coordinates))
    }
}
