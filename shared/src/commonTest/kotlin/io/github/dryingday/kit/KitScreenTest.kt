package io.github.dryingday.kit

import io.github.dryingday.engine.Equipment
import kotlin.test.Test
import kotlin.test.assertEquals

class KitScreenTest {
    @Test
    fun theFirstTapOnANotSaidKitSetsIt() {
        assertEquals(setOf(Equipment.AIRER), toggledKit(null, Equipment.AIRER))
    }

    @Test
    fun tickingAddsToWhatIsThere() {
        assertEquals(
            setOf(Equipment.AIRER, Equipment.TUMBLE_DRYER),
            toggledKit(setOf(Equipment.AIRER), Equipment.TUMBLE_DRYER),
        )
    }

    @Test
    fun untickingTheLastItemLeavesAnEmptyKitNotANotSaidOne() {
        assertEquals(emptySet(), toggledKit(setOf(Equipment.DEHUMIDIFIER), Equipment.DEHUMIDIFIER))
    }

    @Test
    fun untickingKeepsTheOthers() {
        assertEquals(
            setOf(Equipment.HEATED_AIRER),
            toggledKit(setOf(Equipment.AIRER, Equipment.HEATED_AIRER), Equipment.AIRER),
        )
    }
}
