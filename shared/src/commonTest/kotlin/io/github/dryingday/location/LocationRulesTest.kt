package io.github.dryingday.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HOUR = 60L * 60 * 1000

class LocationRulesTest {
    @Test
    fun passiveIsNeverAsked() {
        assertEquals(listOf("network", "gps"), providersToAsk(listOf("passive", "network", "gps")))
        assertEquals(emptyList(), providersToAsk(listOf("passive")))
    }

    @Test
    fun everyOtherEnabledProviderIsAskedInOrder() {
        assertEquals(listOf("fused", "gps", "network"), providersToAsk(listOf("fused", "gps", "passive", "network")))
        assertEquals(emptyList(), providersToAsk(emptyList()))
    }

    @Test
    fun lessThanTwentyFourHoursIsRecentEnough() {
        assertTrue(isRecentEnough(0))
        assertTrue(isRecentEnough(24 * HOUR - 1))
    }

    @Test
    fun twentyFourHoursOrMoreIsNot() {
        assertFalse(isRecentEnough(24 * HOUR))
        assertFalse(isRecentEnough(25 * HOUR))
    }

    @Test
    fun aNegativeAgeIsNot() {
        assertFalse(isRecentEnough(-1))
    }

    @Test
    fun theNewestRecentCandidateWins() {
        val candidates = listOf("old" to 5 * HOUR, "new" to HOUR, "stale" to 30 * HOUR)
        assertEquals("new", newestRecent(candidates))
    }

    @Test
    fun staleCandidatesGiveNothing() {
        assertNull(newestRecent(listOf("a" to 24 * HOUR, "b" to 48 * HOUR)))
        assertNull(newestRecent(emptyList<Pair<String, Long>>()))
    }

    @Test
    fun aNewerStaleOrFaultyAgeDoesNotBeatARecentOne() {
        assertEquals("ok", newestRecent(listOf("future" to -HOUR, "ok" to 2 * HOUR)))
    }
}
