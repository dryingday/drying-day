package io.github.dryingday.weather.openmeteo

import io.github.dryingday.reference.allReferenceCases
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app's mapping must read a response the way scripts/fetch-reference-forecasts.py did, or the reference
 * set wouldn't be testing what users get. On the JVM because common tests can't read files.
 */
class ReferenceParityTest {
    @Test
    fun everyRecordedResponseGivesTheForecastTheGeneratorWrote() {
        for (case in allReferenceCases) {
            val response = File("src/commonTest/testdata/reference/${case.id}.json").readText()

            assertEquals(case.forecast, parseForecast(response), case.id)
        }
    }
}
