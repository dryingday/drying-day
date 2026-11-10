package io.github.dryingday.places

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dryingday.forecast.ForecastCache

/**
 * The saved places as the screens see them. [PlaceStore] isn't observable, so this copies its lists into state:
 * call [refresh] after something else has changed the store. Every refresh also drops the cached forecasts of
 * places no longer saved. See docs/bolts/05-main-screen.md, "What's kept".
 */
class PlacesState(private val store: PlaceStore, private val forecasts: ForecastCache) {
    var places: List<SavedPlace> by mutableStateOf(store.places)
        private set

    var current: SavedPlace? by mutableStateOf(store.current)
        private set

    fun refresh() {
        places = store.places
        current = store.current
        forecasts.retain(store.places.mapTo(HashSet()) { it.coordinates })
    }

    fun select(place: SavedPlace) {
        store.select(place)
        refresh()
    }

    /** Removing the last place leaves no current place, which brings first run back. */
    fun remove(place: SavedPlace) {
        store.remove(place)
        refresh()
    }
}
