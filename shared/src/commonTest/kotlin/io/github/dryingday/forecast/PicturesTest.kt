package io.github.dryingday.forecast

import io.github.dryingday.engine.Condition
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Verdict
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.condition_clear
import io.github.dryingday.shared.resources.condition_cloudy
import io.github.dryingday.shared.resources.condition_drizzle
import io.github.dryingday.shared.resources.condition_fog
import io.github.dryingday.shared.resources.condition_partly_cloudy
import io.github.dryingday.shared.resources.condition_rain
import io.github.dryingday.shared.resources.condition_showers
import io.github.dryingday.shared.resources.condition_snow
import io.github.dryingday.shared.resources.condition_thunder
import io.github.dryingday.shared.resources.load_heavy
import io.github.dryingday.shared.resources.load_light
import io.github.dryingday.shared.resources.verdict_dont_bother
import io.github.dryingday.shared.resources.verdict_partly_dry
import io.github.dryingday.shared.resources.verdict_will_dry
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

    // Written out apart from Pictures.kt, so two swapped entries there fail here.
    @Test
    fun eachConditionHasThePictureOfItsName() {
        val expected = mapOf(
            Condition.CLEAR to Res.drawable.condition_clear,
            Condition.PARTLY_CLOUDY to Res.drawable.condition_partly_cloudy,
            Condition.CLOUDY to Res.drawable.condition_cloudy,
            Condition.FOG to Res.drawable.condition_fog,
            Condition.DRIZZLE to Res.drawable.condition_drizzle,
            Condition.RAIN to Res.drawable.condition_rain,
            Condition.SHOWERS to Res.drawable.condition_showers,
            Condition.SNOW to Res.drawable.condition_snow,
            Condition.THUNDER to Res.drawable.condition_thunder,
        )
        assertEquals(expected, Condition.entries.associateWith { it.picture() })
    }

    @Test
    fun eachVerdictHasThePictureOfItsName() {
        val expected = mapOf(
            Verdict.WILL_DRY to Res.drawable.verdict_will_dry,
            Verdict.PARTLY_DRY to Res.drawable.verdict_partly_dry,
            Verdict.DONT_BOTHER to Res.drawable.verdict_dont_bother,
        )
        assertEquals(expected, Verdict.entries.associateWith { it.picture() })
    }

    @Test
    fun eachSpokenNameIsTheSkyOfItsName() {
        for (condition in Condition.entries) {
            assertEquals(
                "sky_${condition.name.lowercase()}",
                condition.spokenName().key,
            )
        }
    }

    // Written out apart from Pictures.kt, so swapped entries there fail here.
    @Test
    fun eachLoadHasThePictureOfItsName() {
        assertEquals(
            mapOf(Load.LIGHT to Res.drawable.load_light, Load.HEAVY to Res.drawable.load_heavy),
            Load.entries.associateWith { it.picture() },
        )
    }

    @Test
    fun noLoadPictureIsAlsoASkyOrVerdictPicture() {
        val others = Condition.entries.map { it.picture() }.toSet() + Verdict.entries.map { it.picture() }
        assertEquals(emptySet(), Load.entries.map { it.picture() }.toSet() intersect others)
    }
}
