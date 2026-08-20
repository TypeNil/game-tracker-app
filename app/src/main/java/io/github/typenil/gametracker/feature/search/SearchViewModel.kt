package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val rawQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val retryEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val queryRequests: Flow<SearchRequest> = rawQuery
        .map(String::trim)
        .distinctUntilChanged()
        .map { query ->
            SearchRequest(
                query = query,
                shouldDebounce = true
            )
        }

    private val retryRequests: Flow<SearchRequest> = retryEvents.map {
        SearchRequest(
            query = rawQuery.value.trim(),
            shouldDebounce = false
        )
    }

    private val searchResults: Flow<SearchResult> = merge(queryRequests, retryRequests)
        .flatMapLatest { request ->
            if (request.query.isBlank()) {
                flowOf<SearchResult>(SearchResult.Idle)
            } else {
                flow {
                    if (request.shouldDebounce) {
                        delay(SEARCH_DEBOUNCE_MILLIS)
                    }
                    val localFlow = gameRepository.getSearchResultsFlow(request.query)
                    val refreshFlow = flow {
                        emit(RefreshStatus.Loading)
                        val result = gameRepository.searchGames(query = request.query, limit = SEARCH_LIMIT)
                        emit(RefreshStatus.Completed(result))
                    }
                    emitAll(
                        combine(localFlow, refreshFlow) { games, status ->
                            when {
                                games.isNotEmpty() -> SearchResult.Success(query = request.query, games = games)
                                status is RefreshStatus.Loading -> SearchResult.Loading(request.query)
                                status is RefreshStatus.Completed && status.result is AppResult.Error ->
                                    SearchResult.Error(query = request.query, error = status.result.error)
                                else -> SearchResult.Empty(query = request.query)
                            }
                        }
                    )
                }
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(rawQuery, searchResults) { currentRawQuery, result ->
        val trimmedCurrent = currentRawQuery.trim()
        if (trimmedCurrent.isBlank()) {
            SearchUiState(query = currentRawQuery, result = SearchResultUiState.Idle)
        } else {
            when (result) {
                is SearchResult.Idle -> SearchUiState(
                    query = currentRawQuery,
                    result = SearchResultUiState.Loading
                )
                is SearchResult.Loading -> SearchUiState(
                    query = currentRawQuery,
                    result = SearchResultUiState.Loading
                )
                is SearchResult.Success -> {
                    if (result.query == trimmedCurrent) {
                        SearchUiState(
                            query = currentRawQuery,
                            result = SearchResultUiState.Content(result.games)
                        )
                    } else {
                        SearchUiState(
                            query = currentRawQuery,
                            result = SearchResultUiState.Loading
                        )
                    }
                }
                is SearchResult.Empty -> {
                    if (result.query == trimmedCurrent) {
                        SearchUiState(
                            query = currentRawQuery,
                            result = SearchResultUiState.Empty(result.query)
                        )
                    } else {
                        SearchUiState(
                            query = currentRawQuery,
                            result = SearchResultUiState.Loading
                        )
                    }
                }
                is SearchResult.Error -> {
                    if (result.query == trimmedCurrent) {
                        SearchUiState(
                            query = currentRawQuery,
                            result = SearchResultUiState.Error(result.error)
                        )
                    } else {
                        SearchUiState(
                            query = currentRawQuery,
                            result = SearchResultUiState.Loading
                        )
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = SearchUiState(
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
            result = if (savedStateHandle.get<String>(KEY_QUERY)?.trim()?.isNotBlank() == true) {
                SearchResultUiState.Loading
            } else {
                SearchResultUiState.Idle
            }
        )
    )

    fun onQueryChanged(newQuery: String) {
        savedStateHandle[KEY_QUERY] = newQuery.takeCodePoints(MAX_QUERY_LENGTH)
    }

    fun onClearQuery() {
        savedStateHandle[KEY_QUERY] = ""
    }

    fun retry() {
        if (rawQuery.value.trim().isNotBlank()) {
            retryEvents.tryEmit(Unit)
        }
    }

    private data class SearchRequest(
        val query: String,
        val shouldDebounce: Boolean
    )

    private sealed interface RefreshStatus {
        data object Loading : RefreshStatus
        data class Completed(val result: AppResult<Unit>) : RefreshStatus
    }

    private sealed interface SearchResult {
        data object Idle : SearchResult
        data class Loading(val query: String) : SearchResult
        data class Success(val query: String, val games: List<Game>) : SearchResult
        data class Empty(val query: String) : SearchResult
        data class Error(val query: String, val error: AppError) : SearchResult
    }

    companion object {
        const val KEY_QUERY = "search_query"
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val SEARCH_LIMIT = 30
        const val MAX_QUERY_LENGTH = 100

        private fun String.takeCodePoints(maxCodePoints: Int): String {
            if (codePointCount(0, length) <= maxCodePoints) return this
            val endIndex = offsetByCodePoints(0, maxCodePoints)
            return substring(0, endIndex)
        }
    }
}

