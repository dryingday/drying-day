package io.github.dryingday.engine

/** Rule 2: what an hour does to washing on the line. */
internal enum class HourKind {
    /** Rain, or a good chance of it. */
    WET,

    /** Dew or fog. */
    DAMP,
    DRYING,
}
