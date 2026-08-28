package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibrarySnapshot

import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    val rawQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val retryEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val librarySnapshot = MutableStateFlow<LibrarySnapshot>(LibrarySnapshot.Ready(emptyMap()))
    private val editingGameId = MutableStateFlow<Long?>(null)
    private val isLibrarySubmitting = MutableStateFlow(false)
    private var libraryMutationJob: Job? = null
    private val userMessageRes = MutableStateFlow<Int?>(null)

    private val queryRequests: Flow<SearchRequest> = rawQuery
        .map(String::trim)
        .distinctUntilChanged()
        .map { query ->
            SearchRequest(
                query = query,
                shouldDebounce = true,
            )
        }

    private val retryRequests: Flow<SearchRequest> = retryEvents.map {
        SearchRequest(
            query = rawQuery.value.trim(),
            shouldDebounce = false,
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
                        },
                    )
                }
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        rawQuery,
        searchResults,
        combine(librarySnapshot, editingGameId, isLibrarySubmitting, ::LibraryUi),
        userMessageRes,
    ) { currentRawQuery, result, library, message ->
        val trimmedCurrent = currentRawQuery.trim()
        val resultState = if (trimmedCurrent.isBlank()) {
            SearchResultUiState.Idle
        } else {
            when (result) {
                is SearchResult.Idle -> SearchResultUiState.Loading
                is SearchResult.Loading -> SearchResultUiState.Loading
                is SearchResult.Success -> {
                    if (result.query == trimmedCurrent) {
                        SearchResultUiState.Content(result.games)
                    } else {
                        SearchResultUiState.Loading
                    }
                }
                is SearchResult.Empty -> {
                    if (result.query == trimmedCurrent) {
                        SearchResultUiState.Empty(result.query)
                    } else {
                        SearchResultUiState.Loading
                    }
                }
                is SearchResult.Error -> {
                    if (result.query == trimmedCurrent) {
                        SearchResultUiState.Error(result.error)
                    } else {
                        SearchResultUiState.Loading
                    }
                }
            }
        }
        SearchUiState(
            query = currentRawQuery,
            result = resultState,
            librarySnapshot = library.snapshot,
            editingGameId = library.editingGameId,
            isLibrarySubmitting = library.isSubmitting,
            userMessageRes = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = SearchUiState(
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
            result = if (savedStateHandle.get<String>(KEY_QUERY)?.trim()?.isNotBlank() == true) {
                SearchResultUiState.Loading
            } else {
                SearchResultUiState.Idle
            },
        ),
    )

    init {
        viewModelScope.launch {
            try {
                libraryRepository.getLibraryGamesFlow().collect { games ->
                    librarySnapshot.value = LibrarySnapshot.Ready(
                        games.associate { it.entry.gameId to it.entry },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                librarySnapshot.value = LibrarySnapshot.Failed(AppError.UnknownError(error))
                userMessageRes.value = R.string.error_library_load_failed
            }
        }
    }

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

    fun onUserMessageShown() {
        userMessageRes.value = null
    }

    fun onLibraryCardAction(game: Game) {
        when (val snapshot = librarySnapshot.value) {
            LibrarySnapshot.Loading, is LibrarySnapshot.Failed -> return
            is LibrarySnapshot.Ready -> {
                if (snapshot.entries.containsKey(game.id)) {
                    editingGameId.value = game.id
                } else {
                    addToWishlist(game)
                }
            }
        }
    }

    fun onDismissEditLibrary() {
        editingGameId.value = null
    }

    fun addToWishlist(game: Game) {
        viewModelScope.launch {
            when (libraryRepository.addToWishlist(game)) {
                is AppResult.Success -> {
                    gameRepository.refreshGameDetails(game.id, force = false)
                }
                is AppResult.Error -> userMessageRes.value = R.string.error_library_update_failed
            }
        }
    }

    fun onSaveLibraryEntry(
        gameId: Long,
        status: LibraryStatus,
        userRating: Int?,
        hoursPlayed: Int,
        userNotes: String?,
        isFavorite: Boolean,
    ) {
        if (libraryMutationJob?.isActive == true) return
        libraryMutationJob = viewModelScope.launch {
            isLibrarySubmitting.value = true
            try {
                when (
                    libraryRepository.upsertUserEdits(
                        gameId, status, userRating, hoursPlayed, userNotes, isFavorite,
                    )
                ) {
                    is AppResult.Success -> editingGameId.value = null
                    is AppResult.Error -> userMessageRes.value = R.string.error_library_update_failed
                }
            } finally {
                isLibrarySubmitting.value = false
            }
        }
    }

    fun onRemoveFromLibrary(gameId: Long) {
        if (libraryMutationJob?.isActive == true) return
        libraryMutationJob = viewModelScope.launch {
            isLibrarySubmitting.value = true
            try {
                when (libraryRepository.removeGameFromLibrary(gameId)) {
                    is AppResult.Success -> editingGameId.value = null
                    is AppResult.Error -> userMessageRes.value = R.string.error_library_remove_failed
                }
            } finally {
                isLibrarySubmitting.value = false
            }
        }
    }

    private data class LibraryUi(
        val snapshot: LibrarySnapshot,
        val editingGameId: Long?,
        val isSubmitting: Boolean,
    )

    private data class SearchRequest(
        val query: String,
        val shouldDebounce: Boolean,
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
