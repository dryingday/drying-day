package io.github.dryingday.weather.openmeteo

import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

// Results are decoded one by one, so a malformed result is skipped, not the whole list lost.
@Serializable
private data class PlaceSearchResponse(val results: List<JsonElement>? = null)

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

private fun JsonElement.toPlace(): Place? = try {
    val result = openMeteoJson.decodeFromJsonElement<PlaceResult>(this)
    Place(
        name = requireNotNull(result.name?.takeIf { it.isNotBlank() }),
        region = result.admin1?.takeIf { it.isNotBlank() },
        country = result.country?.takeIf { it.isNotBlank() },
        coordinates = Coordinates(requireNotNull(result.latitude), requireNotNull(result.longitude)),
    )
} catch (_: IllegalArgumentException) {
    // SerializationException is one.
    null
}
