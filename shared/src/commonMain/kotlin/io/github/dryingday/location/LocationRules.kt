package io.github.dryingday.location

/** The provider that only relays other apps' fixes, so asking it would not make the phone look. */
const val PASSIVE_PROVIDER = "passive"

/** How long the providers get to answer before the last known location is used instead. */
const val FIX_TIMEOUT_MILLIS = 30_000L

/** A last known location this old or older is not used: it is under 24 hours or nothing. */
const val LAST_KNOWN_LIMIT_MILLIS = 24L * 60 * 60 * 1000

/** The enabled providers to ask, all at once, without `passive` and without repeats. */
fun providersToAsk(enabled: List<String>): List<String> = enabled.filter { it != PASSIVE_PROVIDER }.distinct()

/** True when a location of this age may stand in for a fix. A negative age is a clock fault, so it doesn't. */
fun isRecentEnough(ageMillis: Long): Boolean = ageMillis in 0 until LAST_KNOWN_LIMIT_MILLIS

/** The newest of [candidates], each paired with its age in milliseconds, that [isRecentEnough]. */
fun <T : Any> newestRecent(candidates: List<Pair<T, Long>>): T? =
    candidates.filter { isRecentEnough(it.second) }.minByOrNull { it.second }?.first
