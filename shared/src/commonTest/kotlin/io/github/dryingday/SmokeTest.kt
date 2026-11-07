package io.github.dryingday

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves commonTest runs in `check`. Loading Compose resources needs an Android
 * runtime, so whether the app name reaches the APK is checked on a device.
 */
class SmokeTest {
    @Test
    fun commonTestRuns() {
        assertEquals(2, 1 + 1)
    }
}
