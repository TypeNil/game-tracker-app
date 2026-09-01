package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameSearchQuery
import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
@Suppress("TooManyFunctions")
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    val rawQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    val filters: StateFlow<SearchFilters> = combine(
        savedStateHandle.getStateFlow<List<String>>(KEY_GENRES, emptyList()),
        savedStateHandle.getStateFlow<List<String>>(KEY_PLATFORMS, emptyList()),
        savedStateHandle.getStateFlow(KEY_YEAR, ReleaseYearFilter.ALL.name),
        savedStateHandle.getStateFlow(KEY_RATING, MinRatingFilter.ANY.name),
        savedStateHandle.getStateFlow(KEY_SORT, SearchSortOption.RELEVANCE.name),
    ) { genres, platforms, year, rating, sort ->
        SearchFilters(
            genres = genres.toSet(),
            platforms = platforms.mapNotNull { name ->
                runCatching { PlatformFamily.valueOf(name) }.getOrNull()
            }.toSet(),
            releaseYear = runCatching { ReleaseYearFilter.valueOf(year) }.getOrDefault(ReleaseYearFilter.ALL),
            minRating = runCatching { MinRatingFilter.valueOf(rating) }.getOrDefault(MinRatingFilter.ANY),
            sort = runCatching { SearchSortOption.valueOf(sort) }.getOrDefault(SearchSortOption.RELEVANCE),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchFilters())

    val recentQueries: StateFlow<List<String>> = gameRepository
        .getRecentSearchQueriesFlow(MAX_RECENT_QUERIES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val retryEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val librarySnapshot = MutableStateFlow<LibrarySnapshot>(LibrarySnapshot.Ready(emptyMap()))
    private val editingGameId = MutableStateFlow<Long?>(null)
    private val isLibrarySubmitting = MutableStateFlow(false)
    private var libraryMutationJob: Job? = null
    private val userMessageRes = MutableStateFlow<Int?>(null)

    private val queryRequests: Flow<SearchTrigger> = rawQuery
        .map(String::trim)
        .distinctUntilChanged()
        .map { query ->
            val currentFilters = filters.value
            SearchTrigger(
                domainQuery = currentFilters.toDomainQuery(query),
                sortOption = currentFilters.sort,
                shouldDebounce = query.isNotBlank(),
            )
        }

    private val filterRequests: Flow<SearchTrigger> = filters
        .map { currentFilters ->
            val currentQuery = rawQuery.value.trim()
            SearchTrigger(
                domainQuery = currentFilters.toDomainQuery(currentQuery),
                sortOption = currentFilters.sort,
                shouldDebounce = false,
            )
        }

    private val retryTriggerFlow: Flow<SearchTrigger> = retryEvents.map {
        val currentQuery = rawQuery.value.trim()
        val currentFilters = filters.value
        SearchTrigger(
            domainQuery = currentFilters.toDomainQuery(currentQuery),
            sortOption = currentFilters.sort,
            shouldDebounce = false,
        )
    }

    private val searchTriggerFlow: Flow<SearchTrigger> = merge(queryRequests, filterRequests, retryTriggerFlow)

    private val searchResults: Flow<SearchExecutionResult> = searchTriggerFlow
        .distinctUntilChanged()
        .flatMapLatest { trigger ->
            if (!trigger.domainQuery.shouldSearch) {
                flowOf(SearchExecutionResult.Idle)
            } else {
                flow<SearchExecutionResult> {
                    if (trigger.shouldDebounce) {
                        delay(SEARCH_DEBOUNCE_MILLIS)
                    }
                    val localFlow = gameRepository.getSearchResultsFlow(trigger.domainQuery)
                    val refreshFlow = flow<RefreshStatus> {
                        emit(RefreshStatus.Loading)
                        val result = gameRepository.searchGames(query = trigger.domainQuery, limit = SEARCH_LIMIT)
                        emit(RefreshStatus.Completed(result))
                    }
                    emitAll(
                        combine(localFlow, refreshFlow) { games, status ->
                            val sortedGames = games.applyDisplaySort(
                                sort = trigger.sortOption,
                                qPresent = trigger.domainQuery.query.isNotBlank(),
                            )
                            when {
                                sortedGames.isNotEmpty() -> SearchExecutionResult.Success(
                                    domainQuery = trigger.domainQuery,
                                    games = sortedGames,
                                )
                                status is RefreshStatus.Loading -> SearchExecutionResult.Loading(trigger.domainQuery)
                                status is RefreshStatus.Completed && status.result is AppResult.Error ->
                                    SearchExecutionResult.Error(domainQuery = trigger.domainQuery, error = status.result.error)
                                else -> SearchExecutionResult.Empty(
                                    domainQuery = trigger.domainQuery,
                                    hasConstraints = trigger.domainQuery.hasConstraints,
                                )
                            }
                        }
                    )
                }
            }
        }

    private val searchStateBundle: Flow<SearchStateBundle> = combine(
        rawQuery,
        filters,
        recentQueries,
        searchResults,
    ) { currentRawQuery, currentFilters, recent, result ->
        SearchStateBundle(
            query = currentRawQuery,
            filters = currentFilters,
            recentQueries = recent,
            result = result,
        )
    }

    private val libraryUiFlow: Flow<LibraryUi> = combine(
        librarySnapshot,
        editingGameId,
        isLibrarySubmitting,
    ) { snapshot, editingId, isSubmitting ->
        LibraryUi(snapshot = snapshot, editingGameId = editingId, isSubmitting = isSubmitting)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        searchStateBundle,
        libraryUiFlow,
        userMessageRes,
    ) { searchBundle, library, message ->
        val currentDomainQuery = searchBundle.filters.toDomainQuery(searchBundle.query.trim())
        val resultState = if (!currentDomainQuery.shouldSearch) {
            SearchResultUiState.Idle
        } else {
            when (val res = searchBundle.result) {
                is SearchExecutionResult.Idle,
                is SearchExecutionResult.Loading -> SearchResultUiState.Loading
                is SearchExecutionResult.Success -> {
                    if (res.domainQuery == currentDomainQuery) {
                        SearchResultUiState.Content(res.games)
                    } else {
                        SearchResultUiState.Loading
                    }
                }
                is SearchExecutionResult.Empty -> {
                    if (res.domainQuery == currentDomainQuery) {
                        SearchResultUiState.Empty(
                            query = searchBundle.query,
                            hasConstraints = res.hasConstraints,
                        )
                    } else {
                        SearchResultUiState.Loading
                    }
                }
                is SearchExecutionResult.Error -> {
                    if (res.domainQuery == currentDomainQuery) {
                        SearchResultUiState.Error(res.error)
                    } else {
                        SearchResultUiState.Loading
                    }
                }
            }
        }
        SearchUiState(
            query = searchBundle.query,
            filters = searchBundle.filters,
            recentQueries = searchBundle.recentQueries,
            result = resultState,
            librarySnapshot = library.snapshot,
            editingGameId = library.editingGameId,
            isLibrarySubmitting = library.isSubmitting,
            userMessageRes = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
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

    fun onSelectRecentQuery(query: String) {
        savedStateHandle[KEY_QUERY] = query.takeCodePoints(MAX_QUERY_LENGTH)
    }

    fun onRemoveRecentQuery(query: String) {
        viewModelScope.launch {
            gameRepository.deleteSearchQuery(query)
        }
    }

    fun onClearAllRecentQueries() {
        viewModelScope.launch {
            gameRepository.clearSearchHistory()
        }
    }

    fun onSortSelected(sort: SearchSortOption) {
        savedStateHandle[KEY_SORT] = sort.name
    }

    fun onGenreToggled(genre: String) {
        val current = filters.value.genres
        val updated = if (current.contains(genre)) current - genre else current + genre
        savedStateHandle[KEY_GENRES] = updated.toList()
    }

    fun onPlatformToggled(platform: PlatformFamily) {
        val current = filters.value.platforms
        val updated = if (current.contains(platform)) current - platform else current + platform
        savedStateHandle[KEY_PLATFORMS] = updated.map { it.name }
    }

    fun onReleaseYearSelected(year: ReleaseYearFilter) {
        savedStateHandle[KEY_YEAR] = year.name
    }

    fun onMinRatingSelected(rating: MinRatingFilter) {
        savedStateHandle[KEY_RATING] = rating.name
    }

    fun onApplyFilters(newFilters: SearchFilters) {
        savedStateHandle[KEY_GENRES] = newFilters.genres.toList()
        savedStateHandle[KEY_PLATFORMS] = newFilters.platforms.map { it.name }
        savedStateHandle[KEY_YEAR] = newFilters.releaseYear.name
        savedStateHandle[KEY_RATING] = newFilters.minRating.name
        savedStateHandle[KEY_SORT] = newFilters.sort.name
    }

    fun onResetFilters() {
        savedStateHandle[KEY_GENRES] = emptyList<String>()
        savedStateHandle[KEY_PLATFORMS] = emptyList<String>()
        savedStateHandle[KEY_YEAR] = ReleaseYearFilter.ALL.name
        savedStateHandle[KEY_RATING] = MinRatingFilter.ANY.name
        savedStateHandle[KEY_SORT] = SearchSortOption.RELEVANCE.name
    }

    fun onQuickPresetSelected(genre: String? = null, platform: PlatformFamily? = null) {
        if (genre != null) {
            savedStateHandle[KEY_GENRES] = listOf(genre)
        }
        if (platform != null) {
            savedStateHandle[KEY_PLATFORMS] = listOf(platform.name)
        }
    }

    fun retry() {
        retryEvents.tryEmit(Unit)
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
                is AppResult.Success -> Unit
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


    private data class SearchStateBundle(
        val query: String,
        val filters: SearchFilters,
        val recentQueries: List<String>,
        val result: SearchExecutionResult,
    )
    private data class LibraryUi(
        val snapshot: LibrarySnapshot,
        val editingGameId: Long?,
        val isSubmitting: Boolean,
    )

    private data class SearchTrigger(
        val domainQuery: GameSearchQuery,
        val sortOption: SearchSortOption,
        val shouldDebounce: Boolean,
    )

    private sealed interface RefreshStatus {
        data object Loading : RefreshStatus
        data class Completed(val result: AppResult<Unit>) : RefreshStatus
    }

    private sealed interface SearchExecutionResult {
        data object Idle : SearchExecutionResult
        data class Loading(val domainQuery: GameSearchQuery) : SearchExecutionResult
        data class Success(val domainQuery: GameSearchQuery, val games: List<Game>) : SearchExecutionResult
        data class Empty(val domainQuery: GameSearchQuery, val hasConstraints: Boolean) : SearchExecutionResult
        data class Error(val domainQuery: GameSearchQuery, val error: AppError) : SearchExecutionResult
    }

    companion object {
        const val KEY_QUERY = "search_query"
        const val KEY_GENRES = "search_genres"
        const val KEY_PLATFORMS = "search_platforms"
        const val KEY_YEAR = "search_year"
        const val KEY_RATING = "search_rating"
        const val KEY_SORT = "search_sort"

        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val SEARCH_LIMIT = 30
        const val MAX_QUERY_LENGTH = 100
        const val MAX_RECENT_QUERIES = 10

        private fun String.takeCodePoints(maxCodePoints: Int): String {
            if (codePointCount(0, length) <= maxCodePoints) return this
            val endIndex = offsetByCodePoints(0, maxCodePoints)
            return substring(0, endIndex)
        }
    }
}
