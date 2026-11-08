package io.github.dryingday.engine

/** Rule 2: what an hour does to washing on the line. */
internal enum class HourKind(val isDamp: Boolean = false) {
    /** Rain, or a good chance of it. */
    WET,

    /** Humidity high enough for dew. Damp. */
    DEW(isDamp = true),

    /** Visibility low enough for fog or mist. Damp. */
    FOG(isDamp = true),
    DRYING,
}
