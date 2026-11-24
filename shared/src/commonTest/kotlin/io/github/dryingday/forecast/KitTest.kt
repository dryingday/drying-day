package io.github.dryingday.forecast

import com.russhwolf.settings.MapSettings
import io.github.dryingday.engine.Equipment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KitTest {
    private fun read(stored: String) = readKit(MapSettings("equipment" to stored))

    private fun written(kit: Set<Equipment>) = MapSettings().also { writeKit(it, kit) }.getStringOrNull("equipment")

    private val sixteenKits: List<Set<Equipment>> = (0 until 16).map { mask ->
        Equipment.entries.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
    }

    @Test
    fun noKeyIsNotSaid() {
        assertNull(readKit(MapSettings()))
    }

    @Test
    fun anEmptyStringIsTheEmptySet() {
        assertEquals(emptySet(), read(""))
        assertEquals("", written(emptySet()))
    }

    @Test
    fun everyOneOfTheSixteenKitsRoundTrips() {
        assertEquals(16, sixteenKits.toSet().size)
        for (kit in sixteenKits) {
            val settings = MapSettings()
            writeKit(settings, kit)
            assertEquals(kit, readKit(settings))
        }
    }

    @Test
    fun theStoredOrderIsTheEnumsWhateverTheOrderGiven() {
        assertEquals("AIRER,DEHUMIDIFIER", written(linkedSetOf(Equipment.DEHUMIDIFIER, Equipment.AIRER)))
        assertEquals(
            "AIRER,HEATED_AIRER,DEHUMIDIFIER,TUMBLE_DRYER",
            written(
                linkedSetOf(Equipment.TUMBLE_DRYER, Equipment.DEHUMIDIFIER, Equipment.HEATED_AIRER, Equipment.AIRER),
            ),
        )
    }

    @Test
    fun writingReplacesWhatWasStored() {
        val settings = MapSettings()
        writeKit(settings, setOf(Equipment.AIRER))
        writeKit(settings, setOf(Equipment.TUMBLE_DRYER))
        assertEquals("TUMBLE_DRYER", settings.getStringOrNull("equipment"))
    }

    @Test
    fun unknownCodesAreDroppedAndTheRestKept() {
        assertEquals(setOf(Equipment.AIRER, Equipment.TUMBLE_DRYER), read("AIRER,RADIATOR,TUMBLE_DRYER,"))
    }

    @Test
    fun onlyUnknownCodesIsTheEmptySetNotNotSaid() {
        assertEquals(emptySet(), read("RADIATOR,washer"))
    }

    @Test
    fun duplicatesCountOnce() {
        assertEquals(setOf(Equipment.AIRER, Equipment.DEHUMIDIFIER), read("DEHUMIDIFIER,AIRER,AIRER,DEHUMIDIFIER"))
    }
}
