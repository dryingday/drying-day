package io.github.dryingday.forecast

import androidx.compose.ui.graphics.vector.PathParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawingsParseTest {
    private val expectedSizes =
        mapOf(
            "garment_tee" to (26f to 22f),
            "garment_trousers" to (18f to 30f),
            "garment_shorts" to (20f to 17f),
            "garment_sock" to (11f to 20f),
            "garment_towel" to (22f to 22f),
            "drop" to (9f to 13f),
            "sky_sun" to (340f to 170f),
            "sky_sun_cloud" to (340f to 170f),
            "sky_rain_cloud" to (340f to 170f),
        )

    @Test
    fun theNineDrawingsHaveTheirSizes() {
        assertEquals(expectedSizes.keys, Drawings.all.map { it.name }.toSet())
        assertEquals(expectedSizes.size, Drawings.all.size)
        for (drawing in Drawings.all) {
            assertEquals(expectedSizes.getValue(drawing.name), drawing.width to drawing.height, drawing.name)
        }
    }

    @Test
    fun everyLayerHasARoleAndPathDataThatParses() {
        for (drawing in Drawings.all) {
            assertTrue(drawing.layers.isNotEmpty(), drawing.name)
            assertEquals(drawing.layers.size, drawing.layers.map { it.role }.toSet().size, "${drawing.name}: roles")
            for (layer in drawing.layers) {
                val nodes = PathParser().parsePathString(layer.pathData).toNodes()
                assertTrue(nodes.isNotEmpty(), "${drawing.name} ${layer.role}")
            }
        }
    }
}
