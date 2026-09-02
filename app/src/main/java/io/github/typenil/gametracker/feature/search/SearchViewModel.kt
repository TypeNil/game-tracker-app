package io.github.typenil.gametracker.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import io.github.typenil.gametracker.core.model.SearchInputPolicy
import io.github.typenil.gametracker.core.model.SearchInputValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.Clock
import java.time.Year
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val clock: Clock,
) : ViewModel() {

    val rawQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
    val filterSnapshot: StateFlow<SearchFiltersSnapshot> =
        savedStateHandle.getStateFlow(KEY_FILTERS, SearchFiltersSnapshot())

    val filters: StateFlow<SearchFilters> = filterSnapshot
        .map { it.toDomainFilters() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = savedStateHandle.get<SearchFiltersSnapshot>(KEY_FILTERS)?.toDomainFilters()
                ?: SearchFilters(),
        )

    val recentQueries: StateFlow<List<String>> = gameRepository
        .getRecentSearchQueriesFlow(MAX_RECENT_QUERIES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val librarySnapshot = MutableStateFlow<LibrarySnapshot>(LibrarySnapshot.Ready(emptyMap()))
    private val editingGameId = MutableStateFlow<Long?>(null)
    private val isLibrarySubmitting = MutableStateFlow(false)
    private var libraryMutationJob: Job? = null
    private val userMessageRes = MutableStateFlow<Int?>(null)

    private val textCommands: Flow<SearchCommand> = rawQuery
        .map { raw ->
            SearchCommand(
                domainQuery = filterSnapshot.value
                    .toDomainFilters()
                    .toDomainQuery(raw, clockYear()),
                shouldDebounce = raw.isNotBlank(),
            )
        }

    private val filterCommands: Flow<SearchCommand> = filterSnapshot
        .drop(1)
        .debounce(FILTER_DEBOUNCE_MILLIS)
        .map { snapshot ->
            SearchCommand(
                domainQuery = snapshot
                    .toDomainFilters()
                    .toDomainQuery(rawQuery.value, clockYear()),
                shouldDebounce = false,
            )
        }

    private val automaticCommands: Flow<SearchCommand> = merge(textCommands, filterCommands)
        .distinctUntilChanged { old, new ->
            old.domainQuery == new.domainQuery
        }

    private val pendingSearchLoadStates = LoadStates(
        refresh = LoadState.Loading,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = false),
    )

    val searchResults: Flow<PagingData<Game>> = automaticCommands
        .flatMapLatest { command ->
            val validation = SearchInputPolicy.validate(command.domainQuery.query)
            when {
                validation !is SearchInputValidation.Valid -> flowOf(PagingData.empty())
                !command.domainQuery.shouldSearch -> flowOf(PagingData.empty())
                else -> flow {
                    // The explicit loading generation is emitted before the debounce so the UI
                    // can never present the previous query's cached PagingData (or an idle empty
                    // initial state) under the new query text: the pending generation replaces
                    // cachedIn's replay immediately on every committed command.
                    emit(PagingData.empty(sourceLoadStates = pendingSearchLoadStates))
                    if (command.shouldDebounce) {
                        delay(SEARCH_DEBOUNCE_MILLIS)
                    }
                    // History is optional: a failed write surfaces as a snackbar and must never
                    // gate the paged results themselves.
                    when (gameRepository.recordSearchHistory(command.domainQuery.query)) {
                        is AppResult.Success -> Unit
                        is AppResult.Error -> userMessageRes.value = R.string.error_history_save_failed
                    }
                    emitAll(
                        gameRepository.getPagedSearchResults(
                            query = command.domainQuery,
                            pageSize = PAGE_SIZE,
                        ),
                    )
                }
            }
        }
        .cachedIn(viewModelScope)

    private val searchStateBundle: Flow<SearchStateBundle> = combine(
        rawQuery,
        filters,
        recentQueries,
    ) { currentRawQuery, currentFilters, recent ->
        val validation = SearchInputPolicy.validate(currentRawQuery)
        SearchStateBundle(
            query = currentRawQuery,
            filters = currentFilters,
            recentQueries = recent,
            searchActive = validation is SearchInputValidation.Valid &&
                currentFilters.toDomainQuery(currentRawQuery, clockYear()).shouldSearch,
            queryValidation = validation,
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
        SearchUiState(
            query = searchBundle.query,
            filters = searchBundle.filters,
            recentQueries = searchBundle.recentQueries,
            searchActive = searchBundle.searchActive,
            inputValidation = searchBundle.queryValidation,
            librarySnapshot = library.snapshot,
            editingGameId = library.editingGameId,
            isLibrarySubmitting = library.isSubmitting,
            userMessageRes = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = run {
            val restoredQuery = savedStateHandle.get<String>(KEY_QUERY).orEmpty()
            val restoredFilters = savedStateHandle.get<SearchFiltersSnapshot>(KEY_FILTERS)?.toDomainFilters()
                ?: SearchFilters.Empty
            val restoredValidation = SearchInputPolicy.validate(restoredQuery)
            // The restored state must agree with the validation verdict the interactive path
            // uses: an invalid persisted query never activates the paged result container.
            val restoredSearchActive =
                restoredValidation is SearchInputValidation.Valid &&
                    restoredFilters.toDomainQuery(restoredQuery, clockYear()).shouldSearch
            SearchUiState(
                query = restoredQuery,
                filters = restoredFilters,
                searchActive = restoredSearchActive,
                inputValidation = restoredValidation,
            )
        }
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
        savedStateHandle[KEY_QUERY] = boundedQuery(newQuery)
    }

    fun onClearQuery() {
        savedStateHandle[KEY_QUERY] = ""
    }

    fun onSelectRecentQuery(query: String) {
        savedStateHandle[KEY_QUERY] = boundedQuery(query)
    }

    fun onRemoveRecentQuery(query: String) {
        viewModelScope.launch {
            when (gameRepository.deleteSearchQuery(query)) {
                is AppResult.Success -> Unit
                is AppResult.Error -> userMessageRes.value = R.string.error_history_delete_failed
            }
        }
    }

    fun onClearAllRecentQueries() {
        viewModelScope.launch {
            when (gameRepository.clearSearchHistory()) {
                is AppResult.Success -> Unit
                is AppResult.Error -> userMessageRes.value = R.string.error_history_delete_failed
            }
        }
    }

    fun onSortSelected(sort: SearchSortOption) {
        val currentFilters = filterSnapshot.value.toDomainFilters()
        savedStateHandle[KEY_FILTERS] = currentFilters.copy(sort = sort).toSnapshot()
    }

    fun onGenreToggled(genre: String) {
        val currentFilters = filterSnapshot.value.toDomainFilters()
        val current = currentFilters.genres
        val updated = if (current.contains(genre)) current - genre else current + genre
        savedStateHandle[KEY_FILTERS] = currentFilters.copy(genres = updated).toSnapshot()
    }

    fun onPlatformToggled(platform: PlatformFamily) {
        val currentFilters = filterSnapshot.value.toDomainFilters()
        val current = currentFilters.platforms
        val updated = if (current.contains(platform)) current - platform else current + platform
        savedStateHandle[KEY_FILTERS] = currentFilters.copy(platforms = updated).toSnapshot()
    }

    fun onReleaseYearSelected(year: ReleaseYearFilter) {
        val currentFilters = filterSnapshot.value.toDomainFilters()
        savedStateHandle[KEY_FILTERS] = currentFilters.copy(releaseYear = year).toSnapshot()
    }

    fun onMinRatingSelected(rating: MinRatingFilter) {
        val currentFilters = filterSnapshot.value.toDomainFilters()
        savedStateHandle[KEY_FILTERS] = currentFilters.copy(minRating = rating).toSnapshot()
    }
    fun onApplyFilters(newFilters: SearchFilters) {
        savedStateHandle[KEY_FILTERS] = newFilters.toSnapshot()
    }

    fun onResetFilters() {
        savedStateHandle[KEY_FILTERS] = SearchFilters.Empty.toSnapshot()
    }

    fun onQuickPresetSelected(preset: QuickSearchPreset) {
        when (preset) {
            is QuickSearchPreset.Genre -> {
                savedStateHandle[KEY_FILTERS] = SearchFilters(genres = setOf(preset.name)).toSnapshot()
            }
            is QuickSearchPreset.Platform -> {
                savedStateHandle[KEY_FILTERS] = SearchFilters(platforms = setOf(preset.family)).toSnapshot()
            }
            QuickSearchPreset.Rating80 -> {
                savedStateHandle[KEY_FILTERS] = SearchFilters(minRating = MinRatingFilter.R80).toSnapshot()
            }
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
        val searchActive: Boolean,
        val queryValidation: SearchInputValidation,
    )
    private data class LibraryUi(
        val snapshot: LibrarySnapshot,
        val editingGameId: Long?,
        val isSubmitting: Boolean,
    )

    private data class SearchCommand(
        val domainQuery: GameSearchQuery,
        val shouldDebounce: Boolean,
    )

    private fun clockYear(): Int = Year.now(clock).value

    /**
     * Bounds the raw query stored in [SavedStateHandle] without changing the searched title.
     * Truncating before validation would silently search for a shortened title: instead a raw
     * input is truncated only when its canonical form is provably over the limit (one code point
     * beyond it, so validation surfaces TOO_LONG). A decomposed-but-contract-valid title (e.g.
     * 100 composed characters spelled with combining marks) is stored intact, exactly as the
     * user typed it, and the BFF canonicalizes it.
     */
    private fun boundedQuery(raw: String): String {
        if (raw.codePointCount(0, raw.length) <= MAX_QUERY_LENGTH) return raw
        val canonical = SearchInputPolicy.canonicalize(raw)
        if (canonical != null && canonical.codePointCount(0, canonical.length) <= MAX_QUERY_LENGTH) {
            return raw
        }
        // Truncate the NFC form, never the raw string: cutting a decomposed input mid-combining-mark
        // would NFC-collapse into a valid, silently shortened search instead of surfacing TOO_LONG.
        return Normalizer.normalize(raw, Normalizer.Form.NFC).takeCodePoints(MAX_QUERY_LENGTH + 1)
    }


    companion object {
        const val KEY_QUERY = "search_query"
        const val KEY_FILTERS = "search_filters"

        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val FILTER_DEBOUNCE_MILLIS = 150L
        const val PAGE_SIZE = 20
        const val MAX_QUERY_LENGTH = 100
        const val MAX_RECENT_QUERIES = 10

        private fun String.takeCodePoints(maxCodePoints: Int): String {
            if (codePointCount(0, length) <= maxCodePoints) return this
            val endIndex = offsetByCodePoints(0, maxCodePoints)
            return substring(0, endIndex)
        }
    }
}
