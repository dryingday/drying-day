package io.github.dryingday.places

import com.russhwolf.settings.Settings
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import io.github.dryingday.weather.rounded
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val KEY = "places"
private const val VERSION = 1
private const val KIND_DEVICE = "device"
private const val KIND_SEARCHED = "searched"

// Plain classes, not data classes: no generated toString to print coordinates. [current] is an index into [places].
@Serializable
private class StoredState(val version: Int, val places: List<StoredPlace>, val current: Int?)

@Serializable
private class StoredPlace(
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val region: String? = null,
    val country: String? = null,
)

/**
 * The places the user saved and which one is current, kept in [settings] as one JSON string so a write is all or
 * nothing. Read once here; changes are written at once. Everything saved is rounded first. See
 * docs/bolts/04-location.md.
 */
class PlaceStore(private val settings: Settings) {
    var places: List<SavedPlace> = emptyList()
        private set

    var current: SavedPlace? = null
        private set

    init {
        load()
    }

    /** A duplicate of a saved searched place, by rounded coordinates, selects that one and adds nothing. */
    fun addSearched(place: Place) {
        val rounded = place.copy(coordinates = place.coordinates.rounded())
        val saved = SavedPlace.Searched(rounded)
        val existing = places.firstOrNull { it is SavedPlace.Searched && it.coordinates == rounded.coordinates }
        if (existing == null) places = places + saved
        current = existing ?: saved
        save()
    }

    /** Replaces the device place in its position, or adds one at the end. */
    fun setDevice(coordinates: Coordinates) {
        val saved = SavedPlace.Device(coordinates.rounded())
        places = if (places.any { it is SavedPlace.Device }) {
            places.map { if (it is SavedPlace.Device) saved else it }
        } else {
            places + saved
        }
        current = saved
        save()
    }

    /** Does nothing for a place that isn't saved. */
    fun select(place: SavedPlace) {
        if (place !in places) return
        current = place
        save()
    }

    /** Does nothing for a place that isn't saved. The first remaining place becomes current if this one was. */
    fun remove(place: SavedPlace) {
        if (place !in places) return
        places = places - place
        if (current == place) current = places.firstOrNull()
        save()
    }

    private fun save() {
        val state = StoredState(VERSION, places.map { it.toStored() }, current?.let { places.indexOf(it) })
        settings.putString(KEY, Json.encodeToString(state))
    }

    private fun load() {
        val state = decode()?.takeIf { it.version == VERSION } ?: return
        // Entries that can't be used are dropped, so the saved index may point at a different one after.
        val kept = state.places.map { it.toSaved() }
        places = kept.filterNotNull()
        current = kept.getOrNull(state.current ?: -1) ?: places.firstOrNull()
    }

    // A value that won't decode is no places, and nothing from it goes in a message: it holds coordinates.
    // SerializationException is an IllegalArgumentException.
    private fun decode(): StoredState? = try {
        settings.getStringOrNull(KEY)?.let { Json.decodeFromString<StoredState>(it) }
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun SavedPlace.toStored() = when (this) {
    is SavedPlace.Device -> StoredPlace(KIND_DEVICE, coordinates.latitude, coordinates.longitude)

    is SavedPlace.Searched -> StoredPlace(
        KIND_SEARCHED,
        coordinates.latitude,
        coordinates.longitude,
        place.name,
        place.region,
        place.country,
    )
}

private fun StoredPlace.toSaved(): SavedPlace? {
    val coordinates = try {
        Coordinates(latitude, longitude).rounded()
    } catch (_: IllegalArgumentException) {
        return null
    }
    return when (kind) {
        KIND_DEVICE -> SavedPlace.Device(coordinates)
        KIND_SEARCHED -> name?.let { SavedPlace.Searched(Place(it, region, country, coordinates)) }
        else -> null
    }
}
