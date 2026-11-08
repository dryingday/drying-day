package io.github.dryingday.weather.openmeteo

import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import kotlinx.serialization.Serializable

// Every field has a default so one incomplete result is skipped, not the whole list lost.
@Serializable
private data class PlaceSearchResponse(val results: List<PlaceResult>? = null)

@Serializable
private data class PlaceResult(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val admin1: String? = null,
    val country: String? = null,
)

/**
 * Reads an Open-Meteo place search response. A name that matches nothing comes back with no results at all, which
 * is an empty list. Null means the response can't be read.
 */
internal fun parsePlaces(body: String): List<Place>? = try {
    openMeteoJson.decodeFromString<PlaceSearchResponse>(body).results.orEmpty().mapNotNull { it.toPlace() }
} catch (_: IllegalArgumentException) {
    // SerializationException is one.
    null
}

private fun PlaceResult.toPlace(): Place? = try {
    Place(
        name = requireNotNull(name?.takeIf { it.isNotBlank() }),
        region = admin1,
        country = country,
        coordinates = Coordinates(requireNotNull(latitude), requireNotNull(longitude)),
    )
} catch (_: IllegalArgumentException) {
    null
}
