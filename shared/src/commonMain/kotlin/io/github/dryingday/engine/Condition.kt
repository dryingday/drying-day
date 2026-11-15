package io.github.dryingday.engine

/**
 * The sky for an hour, for the screen: the engine never reads it. In order of severity, the least first, so
 * the more severe of two is the later one. See docs/bolts/07-weather-at-a-glance.md, "The sky".
 */
enum class Condition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SHOWERS,
    SNOW,
    THUNDER,
}
