package io.github.dryingday.forecast

import kotlinx.datetime.LocalDate

// Which garments hang on the row of washing, and where. Pure logic: the screen draws them.
// See docs/bolts/09-washing-line.md, "The row of washing".

/** A kind of garment: its size and where its pegs are, in dp (from its left). The spec's table, as it gives it. */
@Suppress("MagicNumber")
internal enum class GarmentKind(val width: Float, val height: Float, val pegs: List<Float>) {
    T_SHIRT(26f, 22f, listOf(5f, 17f)),
    TROUSERS(18f, 30f, listOf(2f, 11f)),
    SHORTS(20f, 17f, listOf(3f, 12f)),
    SOCK(11f, 20f, listOf(3f)),
    TEA_TOWEL(22f, 22f, listOf(3f, 14f)),
}

/** A garment on the row: its [kind], one of the four garment colours ([colour] 0 to 3) and its left edge [x] in dp. */
internal data class Garment(val kind: GarmentKind, val colour: Int, val x: Float)

internal const val GARMENT_COLOURS = 4

/** The least space between two garments, in dp. */
internal const val MIN_GARMENT_GAP_DP = 4f

// A 64-bit linear congruential generator (Knuth's MMIX constants), written out so the washing doesn't depend on
// any library's choice of algorithm: a day hangs the same washing on every platform and version.
private const val LCG_MULTIPLIER = 6364136223846793005L
private const val LCG_INCREMENT = 1442695040888963407L
private const val SEED_SPREAD = 2654435761L
private const val OUTPUT_SHIFT = 33
private const val WARM_UP_DRAWS = 3

private class Sequence(seed: LocalDate) {
    private var state = seed.toEpochDays() * SEED_SPREAD + LCG_INCREMENT

    init {
        repeat(WARM_UP_DRAWS) { next(1) }
    }

    /** The next number, 0 until [bound]. */
    fun next(bound: Int): Int {
        state = state * LCG_MULTIPLIER + LCG_INCREMENT
        return ((state ushr OUTPUT_SHIFT) % bound).toInt()
    }
}

/**
 * The garments for a row [rowWidth] dp wide whose washing runs from [spanStart] to [spanEnd] dp (bolt 8's bar
 * placement), the same for the same [seed] and span. Garments are drawn from a fixed sequence and added while
 * they fit with at least [MIN_GARMENT_GAP_DP] between them. Two or more are spread, the first starting at the
 * span's start and the last ending at its end. One that fits alone is centred on the span, and so is the sock hung
 * when nothing fits, for a span narrower than a sock or of no width. A lone garment stays inside the row.
 */
internal fun washingRow(spanStart: Float, spanEnd: Float, rowWidth: Float, seed: LocalDate): List<Garment> {
    val start = spanStart.coerceIn(0f, rowWidth)
    val end = spanEnd.coerceIn(start, rowWidth)
    val picked = pickGarments(end - start, Sequence(seed))
    if (picked.size == 1) {
        val only = picked.single()
        val x = ((start + end) / 2 - only.kind.width / 2).coerceIn(0f, maxOf(0f, rowWidth - only.kind.width))
        return listOf(only.copy(x = x))
    }
    val gap = (end - start - picked.sumOf { it.kind.width.toDouble() }.toFloat()) / (picked.size - 1)
    var x = start
    return picked.mapIndexed { index, garment ->
        val left = if (index == picked.lastIndex) end - garment.kind.width else x
        x += garment.kind.width + gap
        garment.copy(x = left)
    }
}

/** The garments, at x 0, that fit in [width] with their gaps: one sock if not even the first does. */
private fun pickGarments(width: Float, sequence: Sequence): List<Garment> {
    val picked = mutableListOf<Garment>()
    var used = 0f
    while (true) {
        val kind = GarmentKind.entries[sequence.next(GarmentKind.entries.size)]
        val colour = sequence.next(GARMENT_COLOURS)
        val needed = used + kind.width + if (picked.isEmpty()) 0f else MIN_GARMENT_GAP_DP
        if (needed > width) {
            if (picked.isEmpty()) picked += Garment(GarmentKind.SOCK, colour, 0f)
            return picked
        }
        picked += Garment(kind, colour, 0f)
        used = needed
    }
}
