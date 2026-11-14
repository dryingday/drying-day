package io.github.dryingday.engine

import kotlin.time.Duration.Companion.hours

/**
 * Every drying threshold in one place, as docs/bolts/02-drying-rules.md sets them. Changing any of these
 * is a high-tier change under docs/review-policy.md, and the spec has to change with it.
 */
internal object DryingThresholds {
    // Rule 2: an hour is wet from either of these.
    const val WET_PRECIPITATION_PROBABILITY_PERCENT = 40.0
    const val WET_PRECIPITATION_MM = 0.2

    // Rule 2: an hour is damp from this humidity, or below this visibility.
    const val DAMP_RELATIVE_HUMIDITY_PERCENT = 95.0
    const val FOG_VISIBILITY_M = 1000.0

    // Rule 2: the Magnus formula for saturation vapour pressure, e_s(T) = A × exp(B × T / (T + C)) kPa.
    // Physics rather than a judgement, but every VPD band edge depends on them.
    const val MAGNUS_KPA = 0.6108
    const val MAGNUS_B = 17.27
    const val MAGNUS_C_CELSIUS = 237.3

    // Rule 2: points for the vapour pressure deficit (kPa); below the first band, none.
    val VPD_POINTS = listOf(
        Band(from = 0.2, value = 0.5),
        Band(from = 0.4, value = 1.0),
        Band(from = 0.7, value = 1.5),
        Band(from = 1.0, value = 2.0),
        // Added after comparing the engine with the reference set's blind labels (docs/reference-set.md):
        // without them, 38 °C at 24 % (5.0 kPa) dried no faster than 20 °C at 57 % (1.0 kPa).
        Band(from = 1.5, value = 2.5),
        Band(from = 2.5, value = 3.0),
    )

    // Rule 2: the wind factor for sustained wind at 10 m (km/h).
    val WIND_FACTOR = listOf(
        Band(from = 0.0, value = 0.6),
        Band(from = 5.0, value = 1.0),
        Band(from = 15.0, value = 1.3),
        Band(from = 30.0, value = 1.5),
    )

    // Rule 2: the sun bonus for mean shortwave radiation (W/m²); below the first band, none.
    val SUN_BONUS = listOf(
        Band(from = 300.0, value = 0.25),
        Band(from = 600.0, value = 0.5),
    )

    // Rule 3: the points each load needs to be dry.
    const val LIGHT_NEED_POINTS = 6.0
    const val HEAVY_NEED_POINTS = 12.0

    // Rule 6: reasons. Air below this deficit dries slowly; below this temperature that's put down to cold.
    const val SLOW_VPD_KPA = 0.4
    const val COLD_BELOW_C = 10.0

    // Rule 6: a wind factor below this is still air.
    const val STILL_AIR_BELOW_FACTOR = 1.0

    // Rule 6: less usable time than this is short daylight.
    val SHORT_DAYLIGHT = 6.hours

    // Rules 6 and 7: "most" is more than half, so twice the time must exceed the span; rule 7 divides a share
    // by one half, which is the same factor.
    const val MOST_SHARE_FACTOR = 2

    // Tips: at most this many a day.
    const val MAX_TIPS = 3

    // Tips: gusts from this speed, a chance of showers from this (up to the wet line), and this much strong sun.
    const val PEG_WELL_GUST_KMH = 40.0
    const val SHOWERS_FROM_PERCENT = 20.0
    const val STRONG_SUN_WM2 = 700.0
    val STRONG_SUN_FOR = 2.hours

    // Indoor plan: outdoor air drier than this is worth letting in, judged over at least this long.
    const val OPEN_WINDOW_BELOW_RH_PERCENT = 70.0
    val INDOOR_TIME_AT_LEAST = 6.hours

    // Rule 5: the verdict line between PARTLY_DRY and DONT_BOTHER, in percent of the load's need.
    const val PARTLY_DRY_FROM_PERCENT = 50
}

/** A table row: [value] applies from [from] up to the next row's [from]. */
internal data class Band(val from: Double, val value: Double)

/** The value of the last band [x] has reached, or [below] when it's under the first. */
internal fun List<Band>.lookup(x: Double, below: Double = 0.0): Double = lastOrNull { x >= it.from }?.value ?: below

/** Rule 3: the points this load needs to be dry. */
internal val Load.needPoints: Double
    get() = when (this) {
        Load.LIGHT -> DryingThresholds.LIGHT_NEED_POINTS
        Load.HEAVY -> DryingThresholds.HEAVY_NEED_POINTS
    }
