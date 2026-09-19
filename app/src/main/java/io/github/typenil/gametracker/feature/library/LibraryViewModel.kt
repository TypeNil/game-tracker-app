package io.github.typenil.gametracker.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntryDraft
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val gameRepository: GameRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val requestedDetailIds = mutableSetOf<Long>()
    private val pendingDetailIds = ArrayDeque<Long>()
    private var hydrationJob: Job? = null

    fun onCardVisible(game: LibraryGame) {
        gameRepository.recordPreview(game.game)
        if (!game.bannerUrl.isNullOrBlank()) return

        val gameId = game.game.id
        if (!requestedDetailIds.add(gameId)) return
        if (pendingDetailIds.size >= MAX_PENDING_DETAIL_IDS) {
            val evictedId = pendingDetailIds.removeFirst()
            requestedDetailIds.remove(evictedId)
        }
        pendingDetailIds.addLast(gameId)
        if (hydrationJob?.isActive == true) return

        hydrationJob = viewModelScope.launch {
            while (pendingDetailIds.isNotEmpty()) {
                val nextId = pendingDetailIds.removeFirst()
                try {
                    gameRepository.refreshGameDetails(
                        id = nextId,
                        force = false,
                    )
                } finally {
                    requestedDetailIds.remove(nextId)
                }
            }
        }
    }
    companion object {
        const val KEY_SELECTED_TAB = "library_selected_tab"
        const val KEY_FILTER_FAVORITES_ONLY = "library_filter_favorites_only"
        const val KEY_SEARCH_QUERY = "library_search_query"
        const val KEY_IS_SEARCH_ACTIVE = "library_is_search_active"
        const val KEY_SORT_OPTION = "library_sort_option"
        private const val MAX_PENDING_DETAIL_IDS = 16
    }

    private val _selectedTab = savedStateHandle.getStateFlow(KEY_SELECTED_TAB, LibraryTab.ALL)
    private val _filterFavoritesOnly = savedStateHandle.getStateFlow(KEY_FILTER_FAVORITES_ONLY, false)
    private val _searchQuery = savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")
    private val _isSearchActive = savedStateHandle.getStateFlow(KEY_IS_SEARCH_ACTIVE, false)
    private val _sortOption = savedStateHandle.getStateFlow(KEY_SORT_OPTION, LibrarySortOption.ADDED_DESC)
    private val _userMessageRes = MutableStateFlow<Int?>(null)
    private val _hoursSaveState = MutableStateFlow<HoursSaveState>(HoursSaveState.Idle)
    private val _libraryMutationState =
        MutableStateFlow<LibraryMutationState>(LibraryMutationState.Idle)
    private var libraryMutationJob: Job? = null

    private val _filterState = combine(
        _selectedTab,
        _filterFavoritesOnly,
        _searchQuery,
        _isSearchActive,
        _sortOption,
    ) { selectedTab, favoritesOnly, query, isSearchActive, sortOption ->
        FilterState(
            selectedTab = selectedTab,
            favoritesOnly = favoritesOnly,
            query = query,
            isSearchActive = isSearchActive,
            sortOption = sortOption,
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.getLibraryGamesFlow(),
        _filterState,
        _userMessageRes,
        _hoursSaveState,
        _libraryMutationState,
    ) { gamesResult, filterState, userMessageRes, hoursSaveState, libraryMutationState ->
        when (gamesResult) {
            is AppResult.Success -> {
                val allGames = gamesResult.data
                val counts = computeTabCounts(allGames)
                val orderedAllGames = sortLibraryGames(allGames, filterState.sortOption)
                val filtered = filterLibraryGames(
                    allGames = orderedAllGames,
                    selectedTab = filterState.selectedTab,
                    favoritesOnly = filterState.favoritesOnly,
                    query = filterState.query,
                )
                LibraryUiState(
                    allGames = orderedAllGames,
                    filteredGames = filtered,
                    selectedTab = filterState.selectedTab,
                    tabCounts = counts,
                    filterFavoritesOnly = filterState.favoritesOnly,
                    searchQuery = filterState.query,
                    isSearchActive = filterState.isSearchActive,
                    sortOption = filterState.sortOption,
                    isLoading = false,
                    userMessageRes = userMessageRes,
                    hoursSaveState = hoursSaveState,
                    libraryMutationState = libraryMutationState,
                )
            }
            is AppResult.Error -> LibraryUiState(
                selectedTab = filterState.selectedTab,
                filterFavoritesOnly = filterState.favoritesOnly,
                searchQuery = filterState.query,
                isSearchActive = filterState.isSearchActive,
                sortOption = filterState.sortOption,
                isLoading = false,
                error = gamesResult.error,
                userMessageRes = userMessageRes,
                hoursSaveState = hoursSaveState,
                libraryMutationState = libraryMutationState,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = LibraryUiState(
            selectedTab = _selectedTab.value,
            filterFavoritesOnly = _filterFavoritesOnly.value,
            searchQuery = _searchQuery.value,
            isSearchActive = _isSearchActive.value,
            sortOption = _sortOption.value,
            isLoading = true,
        )
    )

    fun onTabSelected(tab: LibraryTab) {
        savedStateHandle[KEY_SELECTED_TAB] = tab
    }

    fun onToggleFavoritesOnly() {
        savedStateHandle[KEY_FILTER_FAVORITES_ONLY] = !_filterFavoritesOnly.value
    }

    fun onSearchQueryChanged(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun onToggleSearchActive(active: Boolean) {
        savedStateHandle[KEY_IS_SEARCH_ACTIVE] = active
        if (!active) {
            savedStateHandle[KEY_SEARCH_QUERY] = ""
        }
    }

    fun onSortOptionSelected(sortOption: LibrarySortOption) {
        savedStateHandle[KEY_SORT_OPTION] = sortOption
    }

    fun onClearSearch() {
        savedStateHandle[KEY_SEARCH_QUERY] = ""
    }

    fun onToggleFavorite(gameId: Long) {
        viewModelScope.launch {
            when (libraryRepository.toggleFavorite(gameId)) {
                is AppResult.Success -> Unit
                is AppResult.Error -> {
                    _userMessageRes.value = R.string.error_library_update_failed
                }
            }
        }
    }

    fun onStatusSelected(gameId: Long, status: LibraryStatus) {
        viewModelScope.launch {
            when (libraryRepository.setGameStatus(gameId, status)) {
                is AppResult.Success -> Unit
                is AppResult.Error -> {
                    _userMessageRes.value = R.string.error_library_update_failed
                }
            }
        }
    }

    fun onHoursUpdated(gameId: Long, hours: Int) {
        if (_hoursSaveState.value is HoursSaveState.Saving) return

        _hoursSaveState.value = HoursSaveState.Saving(gameId)
        viewModelScope.launch {
            when (libraryRepository.updateHoursPlayed(gameId, hours)) {
                is AppResult.Success -> {
                    _hoursSaveState.value = HoursSaveState.Saved(gameId)
                }
                is AppResult.Error -> {
                    _hoursSaveState.value = HoursSaveState.Failed(gameId)
                    _userMessageRes.value = R.string.error_library_update_failed
                }
            }
        }
    }

    fun onHoursSaveHandled() {
        _hoursSaveState.value = HoursSaveState.Idle
    }

    fun onSaveLibraryEntry(gameId: Long, draft: LibraryEntryDraft) {
        if (libraryMutationJob?.isActive == true) return

        _libraryMutationState.value = LibraryMutationState.Saving(gameId)
        libraryMutationJob = viewModelScope.launch {
            when (libraryRepository.upsertUserEdits(gameId = gameId, draft = draft)) {
                is AppResult.Success -> {
                    _libraryMutationState.value = LibraryMutationState.Saved(gameId)
                }
                is AppResult.Error -> {
                    _libraryMutationState.value = LibraryMutationState.Failed(gameId)
                    _userMessageRes.value = R.string.error_library_update_failed
                }
            }
        }
    }

    fun onRemoveFromLibrary(gameId: Long) {
        if (libraryMutationJob?.isActive == true) return

        _libraryMutationState.value = LibraryMutationState.Saving(gameId)
        libraryMutationJob = viewModelScope.launch {
            when (libraryRepository.removeGameFromLibrary(gameId)) {
                is AppResult.Success -> {
                    _libraryMutationState.value = LibraryMutationState.Saved(gameId)
                }
                is AppResult.Error -> {
                    _libraryMutationState.value = LibraryMutationState.Failed(gameId)
                    _userMessageRes.value = R.string.error_library_update_failed
                }
            }
        }
    }

    fun onLibraryMutationHandled() {
        _libraryMutationState.value = LibraryMutationState.Idle
    }

    fun onUserMessageShown() {
        _userMessageRes.value = null
    }

    private fun computeTabCounts(allGames: List<LibraryGame>): Map<LibraryTab, Int> {
        val playingCount = allGames.count { it.entry.status == LibraryStatus.PLAYING }
        val wishlistCount = allGames.count { it.entry.status == LibraryStatus.WISHLIST }
        val completedCount = allGames.count { it.entry.status == LibraryStatus.COMPLETED }
        val droppedCount = allGames.count { it.entry.status == LibraryStatus.DROPPED }
        val notInterestedCount = allGames.count { it.entry.status == LibraryStatus.NOT_INTERESTED }
        val allCount = playingCount + wishlistCount + completedCount + droppedCount

        return mapOf(
            LibraryTab.ALL to allCount,
            LibraryTab.PLAYING to playingCount,
            LibraryTab.WISHLIST to wishlistCount,
            LibraryTab.COMPLETED to completedCount,
            LibraryTab.DROPPED to droppedCount,
            LibraryTab.NOT_INTERESTED to notInterestedCount
        )
    }


    private data class FilterState(
        val selectedTab: LibraryTab,
        val favoritesOnly: Boolean,
        val query: String,
        val isSearchActive: Boolean,
        val sortOption: LibrarySortOption,
    )
}
