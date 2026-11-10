package io.github.dryingday.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.weather.Place
import io.github.dryingday.weather.PlaceSearchSource
import io.github.dryingday.weather.SourceError
import io.github.dryingday.weather.SourceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the search screen shows under the box. */
sealed interface SearchStatus {
    data object Idle : SearchStatus

    /** The query is under [MIN_QUERY_LENGTH] characters after trimming, and wasn't sent. */
    data object TooShort : SearchStatus

    data object Searching : SearchStatus

    data class Results(val places: List<Place>) : SearchStatus

    data object NoResults : SearchStatus

    /** [SourceError.UNREACHABLE]. */
    data object Unreachable : SearchStatus

    /** [SourceError.SERVICE] and [SourceError.BAD_RESPONSE]. */
    data object ServiceProblem : SearchStatus
}

const val MIN_QUERY_LENGTH = 2

/**
 * The place search. Text is sent only from [submit], never as it's typed. [scope] belongs to the screen, so leaving
 * it cancels a search in flight. [onChosen] runs after a place has been saved and made current.
 */
class PlaceSearchState(
    private val source: PlaceSearchSource,
    private val places: PlaceStore,
    private val language: String?,
    private val scope: CoroutineScope,
    private val onChosen: () -> Unit,
) {
    var query by mutableStateOf("")
        private set

    var status: SearchStatus by mutableStateOf(SearchStatus.Idle)
        private set

    private var running: Job? = null

    fun updateQuery(text: String) {
        query = text
    }

    /** Cancels any search already running. */
    fun submit() {
        running?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            running = null
            status = SearchStatus.TooShort
            return
        }
        status = SearchStatus.Searching
        running = scope.launch {
            status = when (val result = source.searchPlaces(trimmed, language)) {
                is SourceResult.Success ->
                    if (result.value.isEmpty()) SearchStatus.NoResults else SearchStatus.Results(result.value)

                is SourceResult.Failure ->
                    if (result.error ==
                        SourceError.UNREACHABLE
                    ) {
                        SearchStatus.Unreachable
                    } else {
                        SearchStatus.ServiceProblem
                    }
            }
        }
    }

    fun choose(place: Place) {
        running?.cancel()
        places.addSearched(place)
        onChosen()
    }
}
