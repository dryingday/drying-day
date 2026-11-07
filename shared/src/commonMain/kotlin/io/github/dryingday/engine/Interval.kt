package io.github.dryingday.engine

import kotlin.time.Duration
import kotlin.time.Instant

/** The time from [start] up to [end]; empty when they're equal. */
internal data class Interval(val start: Instant, val end: Instant) {
    init {
        require(start <= end) { "an interval can't end before it starts" }
    }

    val length: Duration get() = end - start
}
