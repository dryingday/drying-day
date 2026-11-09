package io.github.dryingday.weather

import kotlin.math.round

private const val COORDINATE_SCALE = 100.0

/**
 * About a kilometre, and never more precise, whatever the caller has: what a forecast request sends and what is
 * saved. Adding 0.0 turns -0.0 into 0.0.
 */
internal fun Coordinates.rounded() = Coordinates(
    latitude = round(latitude * COORDINATE_SCALE) / COORDINATE_SCALE + 0.0,
    longitude = round(longitude * COORDINATE_SCALE) / COORDINATE_SCALE + 0.0,
)
