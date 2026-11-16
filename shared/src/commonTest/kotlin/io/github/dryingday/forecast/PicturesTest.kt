package io.github.dryingday.forecast

import io.github.dryingday.engine.Condition
import io.github.dryingday.engine.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

class PicturesTest {
    @Test
    fun everyConditionHasItsOwnPictureAndSpokenName() {
        assertEquals(Condition.entries.size, Condition.entries.map { it.picture() }.toSet().size)
        assertEquals(Condition.entries.size, Condition.entries.map { it.spokenName().key }.toSet().size)
    }

    @Test
    fun everyVerdictHasItsOwnPicture() {
        assertEquals(Verdict.entries.size, Verdict.entries.map { it.picture() }.toSet().size)
    }

    @Test
    fun noConditionPictureIsAlsoAVerdictPicture() {
        val conditions = Condition.entries.map { it.picture() }.toSet()
        assertEquals(emptySet(), Verdict.entries.map { it.picture() }.toSet() intersect conditions)
    }

    @Test
    fun spokenNamesAreTheSpecsWords() {
        assertEquals(
            listOf(
                "sky_clear", "sky_partly_cloudy", "sky_cloudy", "sky_fog", "sky_drizzle", "sky_rain", "sky_showers",
                "sky_snow", "sky_thunder",
            ),
            Condition.entries.map { it.spokenName().key },
        )
    }
}
