package io.github.dryingday.places

import com.russhwolf.settings.MapSettings
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val KEY = "places"

private val atlantic = Coordinates(30.1234, -40.3412)
private val pacific = Coordinates(-40.5678, -130.7891)
private val testville = Place("Testville", "Testshire", "Exampleland", Coordinates(30.12, -40.34))
private val otherville = Place("Otherville", null, null, Coordinates(-40.56, -130.78))

// What an entry written by entry("searched", ..., "Testville") loads as.
private val bareTestville = Place("Testville", null, null, Coordinates(30.12, -40.34))

private fun searched(place: Place) = SavedPlace.Searched(place)

private fun state(places: String, current: String = "0", version: Int = 1) =
    """{"version":$version,"places":[$places],"current":$current}"""

private fun entry(kind: String, latitude: Double, longitude: Double, name: String? = null) = buildString {
    append("""{"kind":"$kind","latitude":$latitude,"longitude":$longitude""")
    if (name != null) append(""","name":"$name"""")
    append("}")
}

class PlaceStoreTest {
    private val settings = MapSettings()
    private val store = PlaceStore(settings)

    private fun stored() = checkNotNull(settings.getStringOrNull(KEY))

    private fun loaded(json: String) = PlaceStore(MapSettings(KEY to json))

    @Test
    fun startsEmpty() {
        assertEquals(emptyList(), store.places)
        assertNull(store.current)
        assertNull(settings.getStringOrNull(KEY))
    }

    @Test
    fun aSearchedPlaceIsRoundedBeforeItIsSaved() {
        store.addSearched(Place("Testville", "Testshire", "Exampleland", atlantic))

        assertEquals(searched(testville), store.current)
        val text = stored()
        assertFalse("30.1234" in text || "40.3412" in text, text)
        assertTrue("30.12" in text && "-40.34" in text, text)
    }

    @Test
    fun aDevicePlaceIsRoundedBeforeItIsSaved() {
        store.setDevice(pacific)

        assertEquals(SavedPlace.Device(Coordinates(-40.57, -130.79)), store.current)
        val text = stored()
        assertFalse("40.5678" in text || "130.7891" in text, text)
        assertTrue("-40.57" in text && "-130.79" in text, text)
    }

    @Test
    fun aNewFixReplacesTheDevicePlaceInPlaceAndMakesItCurrent() {
        store.addSearched(testville)
        store.setDevice(atlantic)
        store.addSearched(otherville)

        store.setDevice(pacific)

        assertEquals(
            listOf(searched(testville), SavedPlace.Device(Coordinates(-40.57, -130.79)), searched(otherville)),
            store.places,
        )
        assertEquals(store.places[1], store.current)
    }

    @Test
    fun aFirstFixIsAddedAtTheEndAndMadeCurrent() {
        store.addSearched(testville)
        store.addSearched(otherville)

        store.setDevice(atlantic)

        assertEquals(SavedPlace.Device(Coordinates(30.12, -40.34)), store.places.last())
        assertEquals(3, store.places.size)
        assertEquals(store.places.last(), store.current)
    }

    @Test
    fun aDuplicateSearchedPlaceSelectsTheExistingOne() {
        store.addSearched(testville)
        store.addSearched(otherville)

        store.addSearched(Place("Testville again", null, null, atlantic))

        assertEquals(listOf(searched(testville), searched(otherville)), store.places)
        assertEquals(searched(testville), store.current)
    }

    @Test
    fun aDeviceFixAtASearchedPlaceIsNotADuplicate() {
        store.addSearched(testville)
        store.setDevice(atlantic)

        assertEquals(2, store.places.size)
        assertEquals(SavedPlace.Device(testville.coordinates), store.current)
    }

    @Test
    fun aSearchedPlaceAtTheDeviceFixIsNotADuplicate() {
        store.setDevice(atlantic)
        store.addSearched(testville)

        assertEquals(listOf(SavedPlace.Device(testville.coordinates), searched(testville)), store.places)
        assertEquals(searched(testville), store.current)
    }

    @Test
    fun newPlacesGoAtTheEndAndBecomeCurrent() {
        store.addSearched(testville)
        store.addSearched(otherville)

        assertEquals(listOf(searched(testville), searched(otherville)), store.places)
        assertEquals(searched(otherville), store.current)
    }

    @Test
    fun selectingChangesTheCurrentPlace() {
        store.addSearched(testville)
        store.addSearched(otherville)

        store.select(searched(testville))

        assertEquals(searched(testville), store.current)
    }

    @Test
    fun selectingOrRemovingAPlaceThatIsNotSavedDoesNothing() {
        store.addSearched(testville)
        val before = stored()

        store.select(searched(otherville))
        store.remove(searched(otherville))

        assertEquals(listOf(searched(testville)), store.places)
        assertEquals(searched(testville), store.current)
        assertEquals(before, stored())
    }

    @Test
    fun removingTheCurrentPlaceMakesTheFirstRemainingCurrent() {
        store.addSearched(testville)
        store.setDevice(atlantic)
        store.addSearched(otherville)

        store.remove(searched(otherville))

        assertEquals(searched(testville), store.current)
        assertEquals(2, store.places.size)
    }

    @Test
    fun removingAnotherPlaceLeavesTheCurrentOne() {
        store.addSearched(testville)
        store.addSearched(otherville)

        store.remove(searched(testville))

        assertEquals(listOf(searched(otherville)), store.places)
        assertEquals(searched(otherville), store.current)
    }

    @Test
    fun removingTheLastPlaceLeavesNoCurrentPlace() {
        store.addSearched(testville)

        store.remove(searched(testville))

        assertEquals(emptyList(), store.places)
        assertNull(store.current)
        assertNull(PlaceStore(settings).current)
    }

    @Test
    fun aNewStoreOnTheSameSettingsHasTheSamePlaces() {
        store.addSearched(testville)
        store.setDevice(pacific)
        store.addSearched(otherville)
        store.select(searched(testville))

        val again = PlaceStore(settings)

        assertEquals(store.places, again.places)
        assertEquals(searched(testville), again.current)
    }

    @Test
    fun valuesThatCannotBeDecodedMeanNoPlaces() {
        for (text in listOf("", "not json", "{", "[]", """{"version":1}""", """{"version":1,"places":3}""")) {
            val broken = loaded(text)

            assertEquals(emptyList(), broken.places, text)
            assertNull(broken.current, text)
        }
    }

    @Test
    fun aBrokenValueIsOverwrittenOnTheNextSave() {
        val broken = MapSettings(KEY to "not json")

        PlaceStore(broken).addSearched(testville)

        assertEquals(listOf(searched(testville)), PlaceStore(broken).places)
    }

    @Test
    fun anotherVersionMeansNoPlaces() {
        val other = loaded(state(entry("searched", 30.12, -40.34, "Testville"), version = 2))

        assertEquals(emptyList(), other.places)
        assertNull(other.current)
    }

    @Test
    fun anEntryWithInvalidCoordinatesIsDropped() {
        val text = state(
            entry("searched", 91.0, -40.34, "Badville") + "," + entry("searched", 30.12, -40.34, "Testville"),
            current = "1",
        )

        assertEquals(listOf(searched(bareTestville)), loaded(text).places)
    }

    @Test
    fun anEntryOfAnUnknownKindOrWithoutANameIsDropped() {
        val dropped = loaded(state(entry("other", 1.0, 2.0, "X") + "," + entry("searched", 1.0, 2.0)))

        assertEquals(emptyList(), dropped.places)
        assertNull(dropped.current)
    }

    @Test
    fun theCurrentPlaceStaysCurrentWhenAnEarlierEntryIsDropped() {
        val text = state(
            entry("searched", 91.0, 0.0, "Badville") + "," +
                entry("searched", 30.12, -40.34, "Testville") + "," +
                entry("device", -40.56, -130.78),
            current = "2",
        )

        val shifted = loaded(text)

        assertEquals(2, shifted.places.size)
        assertEquals(SavedPlace.Device(otherville.coordinates), shifted.current)
    }

    @Test
    fun aDroppedCurrentPlaceFallsBackToTheFirstRemaining() {
        val text = state(
            entry("searched", 30.12, -40.34, "Testville") + "," + entry("searched", 0.0, 181.0, "Badville"),
            current = "1",
        )

        assertEquals(searched(bareTestville), loaded(text).current)
    }

    @Test
    fun aDanglingCurrentPlaceFallsBackToTheFirst() {
        val places = entry("searched", 30.12, -40.34, "Testville")

        for (current in listOf("5", "-1", "null")) {
            assertEquals(searched(bareTestville), loaded(state(places, current = current)).current, current)
        }
    }

    @Test
    fun savedPlacesDoNotPrintTheirValues() {
        store.addSearched(testville)
        val texts = listOf(
            searched(testville).toString(),
            SavedPlace.Device(atlantic).toString(),
            store.places.toString(),
        )

        for (text in texts) {
            for (value in listOf("Testville", "Testshire", "Exampleland", "30.1", "40.3")) {
                assertFalse(value in text, text)
            }
        }
    }
}
