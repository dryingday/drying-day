package io.github.dryingday

import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.app_name
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderScreenTest {
    @Test
    fun titleComesFromTheAppNameStringResource() {
        assertEquals(Res.string.app_name, PLACEHOLDER_TITLE)
    }
}
