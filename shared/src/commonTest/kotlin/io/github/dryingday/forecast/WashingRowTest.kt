package io.github.dryingday.forecast

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val seed = LocalDate(2025, 6, 14)
private const val EPSILON = 0.001f
private const val ROW = 800f

private fun Garment.end() = x + kind.width

private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)

class WashingRowTest {
    @Test
    fun theSameSeedAndSpanGiveTheSameGarments() {
        assertEquals(washingRow(10f, 300f, 400f, seed), washingRow(10f, 300f, 400f, seed))
    }

    @Test
    fun differentDaysCanHangDifferentWashing() {
        val days = (0 until 14).map { washingRow(0f, 300f, 300f, seed.plusDays(it)) }
        assertTrue(days.toSet().size > 1)
    }

    @Test
    fun aWiderSpanKeepsTheSameFirstGarments() {
        val narrow = washingRow(0f, 150f, ROW, seed)
        val wide = washingRow(0f, 300f, ROW, seed)
        assertEquals(narrow.map { it.kind to it.colour }, wide.take(narrow.size).map { it.kind to it.colour })
    }

    @Test
    fun twoOrMoreSpreadFromTheStartToTheEndWithGapsAndNothingCutOff() {
        for (width in listOf(60f, 100f, 180f, 300f, 700f)) {
            val row = washingRow(20f, 20f + width, ROW, seed)
            assertTrue(row.size >= 2, "width $width")
            assertEquals(20f, row.first().x, EPSILON)
            assertEquals(20f + width, row.last().end(), EPSILON)
            row.zipWithNext().forEach { (a, b) -> assertTrue(b.x - a.end() >= MIN_GARMENT_GAP_DP - EPSILON) }
            assertTrue(row.all { it.x >= 0f && it.end() <= ROW })
            assertTrue(row.all { it.colour in 0 until GARMENT_COLOURS })
        }
    }

    @Test
    fun oneThatFitsAloneIsCentred() {
        val first = washingRow(0f, 1000f, 1000f, seed).first().kind
        val span = first.width + 1f
        val row = washingRow(100f, 100f + span, 400f, seed)

        assertEquals(1, row.size)
        assertEquals(first, row.single().kind)
        assertEquals(100f + span / 2, row.single().x + first.width / 2, EPSILON)
    }

    @Test
    fun aSpanNarrowerThanASockHangsOneSockCentred() {
        for (width in listOf(0f, 3f, 10.9f)) {
            val row = washingRow(100f, 100f + width, 400f, seed)

            assertEquals(1, row.size)
            assertEquals(GarmentKind.SOCK, row.single().kind)
            assertEquals(100f + width / 2, row.single().x + GarmentKind.SOCK.width / 2, EPSILON)
        }
    }

    @Test
    fun aLoneGarmentAtTheRowsEdgeIsKeptInsideIt() {
        val atStart = washingRow(0f, 0f, 400f, seed).single()
        val atEnd = washingRow(400f, 400f, 400f, seed).single()

        assertEquals(0f, atStart.x, EPSILON)
        assertEquals(400f, atEnd.end(), EPSILON)
    }

    @Test
    fun aRowNarrowerThanAGarmentStillHangsOneAtItsStart() {
        val row = washingRow(0f, 0f, 5f, seed)

        assertEquals(1, row.size)
        assertEquals(0f, row.single().x, EPSILON)
    }

    @Test
    fun theSizesAndPegsAreTheSpecs() {
        assertEquals(listOf(26f, 22f), GarmentKind.T_SHIRT.size())
        assertEquals(listOf(5f, 17f), GarmentKind.T_SHIRT.pegs)
        assertEquals(listOf(18f, 30f), GarmentKind.TROUSERS.size())
        assertEquals(listOf(2f, 11f), GarmentKind.TROUSERS.pegs)
        assertEquals(listOf(20f, 17f), GarmentKind.SHORTS.size())
        assertEquals(listOf(3f, 12f), GarmentKind.SHORTS.pegs)
        assertEquals(listOf(11f, 20f), GarmentKind.SOCK.size())
        assertEquals(listOf(3f), GarmentKind.SOCK.pegs)
        assertEquals(listOf(22f, 22f), GarmentKind.TEA_TOWEL.size())
        assertEquals(listOf(3f, 14f), GarmentKind.TEA_TOWEL.pegs)
    }

    private fun GarmentKind.size() = listOf(width, height)
}
