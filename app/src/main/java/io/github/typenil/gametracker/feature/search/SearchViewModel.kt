package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val savedStateHandle: SavedStateHandle,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    val rawQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val retryTrigger = MutableStateFlow(0L)

    private val searchResults: Flow<SearchResult> = combine(
        rawQuery
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce { query -> if (query.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS },
        retryTrigger
    ) { query, retryId ->
        SearchRequest(query = query, retryId = retryId)
    }.flatMapLatest { request ->
        if (request.query.isBlank()) {
            flowOf(SearchResult.Idle)
        } else {
            flow {
                emit(SearchResult.Loading(request.query))
                when (val result = gameRepository.searchGames(query = request.query, limit = SEARCH_LIMIT)) {
                    is AppResult.Success -> {
                        if (result.data.isEmpty()) {
                            emit(SearchResult.Empty(request.query))
                        } else {
                            emit(SearchResult.Success(query = request.query, games = result.data))
                        }
                    }
                    is AppResult.Error -> {
                        emit(SearchResult.Error(query = request.query, error = result.error))
                    }
                }
            }.flowOn(ioDispatcher)
        }
    }

    val uiState: StateFlow<SearchUiState> = combine(rawQuery, searchResults) { currentRawQuery, result ->
        val trimmedCurrent = currentRawQuery.trim()
        if (trimmedCurrent.isBlank()) {
            SearchUiState(query = currentRawQuery, games = emptyList(), status = SearchStatus.Idle)
        } else {
            when (result) {
                is SearchResult.Idle -> SearchUiState(
                    query = currentRawQuery,
                    games = emptyList(),
                    status = SearchStatus.Idle
                )
                is SearchResult.Loading -> SearchUiState(
                    query = currentRawQuery,
                    games = emptyList(),
                    status = SearchStatus.Loading
                )
                is SearchResult.Success -> {
                    if (result.query == trimmedCurrent) {
                        SearchUiState(
                            query = currentRawQuery,
                            games = result.games,
                            status = SearchStatus.Content
                        )
                    } else {
                        SearchUiState(
                            query = currentRawQuery,
                            games = emptyList(),
                            status = SearchStatus.Loading
                        )
                    }
                }
                is SearchResult.Empty -> {
                    if (result.query == trimmedCurrent) {
                        SearchUiState(
                            query = currentRawQuery,
                            games = emptyList(),
                            status = SearchStatus.Empty
                        )
                    } else {
                        SearchUiState(
                            query = currentRawQuery,
                            games = emptyList(),
                            status = SearchStatus.Loading
                        )
                    }
                }
                is SearchResult.Error -> {
                    if (result.query == trimmedCurrent) {
                        SearchUiState(
                            query = currentRawQuery,
                            games = emptyList(),
                            status = SearchStatus.Error(result.error)
                        )
                    } else {
                        SearchUiState(
                            query = currentRawQuery,
                            games = emptyList(),
                            status = SearchStatus.Loading
                        )
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
            status = if (savedStateHandle.get<String>(KEY_QUERY)?.trim()?.isNotBlank() == true) {
                SearchStatus.Loading
            } else {
                SearchStatus.Idle
            }
        )
    )

    fun onQueryChanged(newQuery: String) {
        val clamped = if (newQuery.length > MAX_QUERY_LENGTH) {
            newQuery.take(MAX_QUERY_LENGTH)
        } else {
            newQuery
        }
        savedStateHandle[KEY_QUERY] = clamped
    }

    fun onClearQuery() {
        savedStateHandle[KEY_QUERY] = ""
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private data class SearchRequest(
        val query: String,
        val retryId: Long
    )

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
    }
}
