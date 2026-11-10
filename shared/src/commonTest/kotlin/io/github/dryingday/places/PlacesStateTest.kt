package io.github.dryingday.places

import com.russhwolf.settings.MapSettings
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val seaville = Place("Seaville", null, null, Coordinates(30.12, -40.34))
private val deepsea = Place("Deepsea", null, null, Coordinates(-10.5, 150.25))

class PlacesStateTest {
    private val store = PlaceStore(MapSettings())
    private val state = PlacesState(store)

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
}
