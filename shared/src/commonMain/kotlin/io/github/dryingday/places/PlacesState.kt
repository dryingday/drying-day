package io.github.dryingday.places

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The saved places as the screens see them. [PlaceStore] isn't observable, so this copies its lists into state:
 * call [refresh] after something else has changed the store.
 */
class PlacesState(private val store: PlaceStore) {
    var places: List<SavedPlace> by mutableStateOf(store.places)
        private set

    var current: SavedPlace? by mutableStateOf(store.current)
        private set

    fun refresh() {
        places = store.places
        current = store.current
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
