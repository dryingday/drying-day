package io.github.dryingday.places

import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place

/** A place the user has saved. Where someone lives is private, so neither kind prints its values. */
sealed interface SavedPlace {
    val coordinates: Coordinates

    /** One found by name. */
    data class Searched(val place: Place) : SavedPlace {
        override val coordinates get() = place.coordinates

        override fun toString() = "SavedPlace.Searched(hidden)"
    }

    /** Where the device was. Its name is translated when shown, so it isn't kept. */
    data class Device(override val coordinates: Coordinates) : SavedPlace {
        override fun toString() = "SavedPlace.Device(hidden)"
    }
}
