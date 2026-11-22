package io.github.dryingday.forecast

import androidx.compose.ui.graphics.vector.PathParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Reads the SVG sources in art/ from the repository (the tests run in the shared module's directory, as
// ReferenceParityTest does) and checks each path's id and data against Drawings.kt.
class DrawingsTest {
    private class SvgPath(val id: String, val d: String)

    private val pathElement = Regex("<path\\b[^>]*>")

    private fun attribute(element: String, name: String): String =
        Regex("\\s$name=\"([^\"]*)\"").find(element)?.groupValues?.get(1) ?: error("no $name in $element")

    private fun svgPaths(name: String): List<SvgPath> {
        val text = File("../art/$name.svg").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        return pathElement.findAll(text).map { SvgPath(attribute(it.value, "id"), attribute(it.value, "d")) }.toList()
    }

    @Test
    fun eachSvgHasTheSameLayersAsTheKotlinData() {
        for (drawing in Drawings.all) {
            val svg = svgPaths(drawing.name)
            assertEquals(drawing.layers.map { it.role }, svg.map { it.id }, "${drawing.name}: layers")
            for ((layer, path) in drawing.layers.zip(svg)) {
                assertEquals(layer.pathData, path.d, "${drawing.name} ${layer.role}")
            }
        }
    }

    @Test
    fun eachSvgHasItsViewportSize() {
        for (drawing in Drawings.all) {
            val text = File("../art/${drawing.name}.svg").readText()
            val root = Regex("<svg\\b[^>]*>").find(text)?.value ?: error("no svg element in ${drawing.name}")
            val w = drawing.width.toInt()
            val h = drawing.height.toInt()
            assertEquals("0 0 $w $h", attribute(root, "viewBox"), drawing.name)
            assertEquals("$w", attribute(root, "width"), drawing.name)
            assertEquals("$h", attribute(root, "height"), drawing.name)
        }
    }

    @Test
    fun eachPathParses() {
        for (drawing in Drawings.all) {
            for (layer in drawing.layers) {
                assertTrue(PathParser().parsePathString(layer.pathData).toNodes().isNotEmpty(), layer.role)
            }
        }
    }
}
