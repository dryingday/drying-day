package io.github.dryingday.search

import com.russhwolf.settings.MapSettings
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.weather.Attribution
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.Place
import io.github.dryingday.weather.PlaceSearchSource
import io.github.dryingday.weather.SourceError
import io.github.dryingday.weather.SourceResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val seaPlace = Place("Seaville", "Northshire", "Oceania", Coordinates(30.1234, -40.3412))

private class FakeSearch : PlaceSearchSource {
    override val id = "fake"
    override val attributions: List<Attribution> = emptyList()
    val queries = mutableListOf<Pair<String, String?>>()
    var answer = CompletableDeferred<SourceResult<List<Place>>>()
    var cancelled = 0

    override suspend fun searchPlaces(query: String, language: String?): SourceResult<List<Place>> {
        queries += query to language
        try {
            return answer.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled++
            throw e
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchStateTest {
    private val source = FakeSearch()
    private val store = PlaceStore(MapSettings())
    private var chosen = 0

    private fun TestScope.state() = PlaceSearchState(source, store, "en", backgroundScope) { chosen++ }

    @Test
    fun typingSendsNothing() = runTest {
        val state = state()
        state.updateQuery("Sea")
        state.updateQuery("Seaville")
        runCurrent()
        assertTrue(source.queries.isEmpty())
        assertEquals(SearchStatus.Idle, state.status)
    }

    @Test
    fun shortQueryIsNotSent() = runTest {
        val state = state()
        state.updateQuery("  a ")
        state.submit()
        runCurrent()
        assertTrue(source.queries.isEmpty())
        assertEquals(SearchStatus.TooShort, state.status)
    }

    @Test
    fun submitSendsTrimmedQueryAndLanguage() = runTest {
        val state = state()
        state.updateQuery("  Seaville ")
        state.submit()
        assertEquals(SearchStatus.Searching, state.status)
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("Seaville" to "en"), source.queries)
    }

    @Test
    fun resultsAreShown() = runTest {
        val state = state()
        state.updateQuery("Sea")
        state.submit()
        runCurrent()
        source.answer.complete(SourceResult.Success(listOf(seaPlace)))
        runCurrent()
        assertEquals(SearchStatus.Results(listOf(seaPlace)), state.status)
    }

    @Test
    fun noResults() = runTest {
        val state = state()
        state.updateQuery("Sea")
        state.submit()
        runCurrent()
        source.answer.complete(SourceResult.Success(emptyList<Place>()))
        runCurrent()
        assertEquals(SearchStatus.NoResults, state.status)
    }

    @Test
    fun failuresMapToTwoMessages() = runTest {
        for ((error, expected) in listOf(
            SourceError.UNREACHABLE to SearchStatus.Unreachable,
            SourceError.SERVICE to SearchStatus.ServiceProblem,
            SourceError.BAD_RESPONSE to SearchStatus.ServiceProblem,
        )) {
            val s = FakeSearch()
            val state = PlaceSearchState(s, store, null, backgroundScope) {}
            state.updateQuery("Sea")
            state.submit()
            runCurrent()
            s.answer.complete(SourceResult.Failure(error))
            runCurrent()
            assertEquals(expected, state.status)
        }
    }

    @Test
    fun anotherGoAfterFailureSendsAgain() = runTest {
        val state = state()
        state.updateQuery("Sea")
        state.submit()
        runCurrent()
        source.answer.complete(SourceResult.Failure(SourceError.UNREACHABLE))
        runCurrent()
        source.answer = CompletableDeferred()
        state.submit()
        runCurrent()
        assertEquals(2, source.queries.size)
        assertEquals(SearchStatus.Searching, state.status)
    }

    @Test
    fun newSearchCancelsTheRunningOne() = runTest {
        val state = state()
        state.updateQuery("Sea")
        state.submit()
        runCurrent()
        state.updateQuery("Seaville")
        state.submit()
        runCurrent()
        assertEquals(1, source.cancelled)
        source.answer.complete(SourceResult.Success(listOf(seaPlace)))
        runCurrent()
        assertEquals(SearchStatus.Results(listOf(seaPlace)), state.status)
    }

    @Test
    fun shortQueryCancelsTheRunningSearch() = runTest {
        val state = state()
        state.updateQuery("Sea")
        state.submit()
        runCurrent()
        state.updateQuery("S")
        state.submit()
        runCurrent()
        assertEquals(1, source.cancelled)
        assertEquals(SearchStatus.TooShort, state.status)
    }

    @Test
    fun choosingSavesMakesCurrentAndCloses() = runTest {
        val state = state()
        state.choose(seaPlace)
        assertEquals(Coordinates(30.12, -40.34), store.current?.coordinates)
        assertEquals(1, chosen)
    }

    @Test
    fun leavingTheScreenCancelsTheSearchAndSavesNothing() = runTest {
        val screen = CoroutineScope(Job(backgroundScope.coroutineContext[Job]))
        val state = PlaceSearchState(source, store, "en", screen) { chosen++ }
        state.updateQuery("Sea")
        state.submit()
        runCurrent()
        screen.cancel()
        runCurrent()
        assertEquals(1, source.cancelled)
        assertEquals(null, store.current)
        assertEquals(0, chosen)
    }

    @Test
    fun describeSkipsAbsentParts() {
        assertEquals("Seaville, Northshire, Oceania", describe(seaPlace))
        assertEquals("Seaville, Oceania", describe(Place("Seaville", null, "Oceania", seaPlace.coordinates)))
        assertEquals("Seaville", describe(Place("Seaville", null, null, seaPlace.coordinates)))
    }
}
