package io.github.dryingday.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CoordinatesTest {
    @Test
    fun acceptsTheLimits() {
        assertEquals(-90.0, Coordinates(-90.0, -180.0).latitude)
        assertEquals(90.0, Coordinates(90.0, 180.0).latitude)
        assertEquals(-180.0, Coordinates(-90.0, -180.0).longitude)
        assertEquals(180.0, Coordinates(90.0, 180.0).longitude)
    }

    @Test
    fun rejectsLatitudeOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Coordinates(90.01, 0.0) }
        assertFailsWith<IllegalArgumentException> { Coordinates(-90.01, 0.0) }
    }

    @Test
    fun rejectsLongitudeOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, 180.01) }
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, -180.01) }
    }

    @Test
    fun rejectsValuesThatAreNotFinite() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Coordinates(bad, 0.0) }
            assertFailsWith<IllegalArgumentException> { Coordinates(0.0, bad) }
        }
    }

    @Test
    fun printingDoesNotShowTheValues() {
        val coordinates = Coordinates(12.34, 56.78)
        val texts =
            listOf(
                coordinates.toString(),
                Place("Testville", null, null, coordinates).toString(),
                SourceResult.Success(listOf(Place("Testville", null, null, coordinates))).toString(),
            )

        for (text in texts) {
            assertFalse(text.contains("12.34"), text)
            assertFalse(text.contains("56.78"), text)
        }
    }

    @Test
    fun theErrorDoesNotContainTheValues() {
        val latitude = assertFailsWith<IllegalArgumentException> { Coordinates(123.45, 67.89) }
        val longitude = assertFailsWith<IllegalArgumentException> { Coordinates(12.34, 567.89) }

        assertFalse(checkNotNull(latitude.message).contains("123.45"))
        assertFalse(checkNotNull(latitude.message).contains("67.89"))
        assertFalse(checkNotNull(longitude.message).contains("12.34"))
        assertFalse(checkNotNull(longitude.message).contains("567.89"))
    }
}
