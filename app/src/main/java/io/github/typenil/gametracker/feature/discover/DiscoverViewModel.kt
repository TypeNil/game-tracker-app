package io.github.typenil.gametracker.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.SplashHold
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.connectivity.reconnects
import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val librarySeeder: LibrarySeeder,
    private val networkMonitor: NetworkMonitor? = null,
    private val splashHold: SplashHold = SplashHold(),
) : ViewModel() {

    private val selectionManager = DiscoverSelectionManager()
    private val railLoader = DiscoverRailLoader(gameRepository, viewModelScope)
    private val forYouLoader = DiscoverForYouLoader(
        gameRepository = gameRepository,
        libraryRepository = libraryRepository,
        scope = viewModelScope,
        onUserMessage = { userMessageRes.value = it },
        isRefreshing = { refreshing.value },
    )

    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val userMessageRes = MutableStateFlow<Int?>(null)
    private val librarySnapshot = MutableStateFlow<LibrarySnapshot>(LibrarySnapshot.Loading)
    private val editingGameId = MutableStateFlow<Long?>(null)
    private val isLibrarySubmitting = MutableStateFlow(false)
    private var libraryMutationJob: Job? = null

    private var lastLibraryEntries: Set<LibraryEntry>? = null
    private var hydrateJob: Job? = null

    val uiState: StateFlow<DiscoverUiState> = combine(
        selectionManager.selectedTab,
        combine(
            forYouLoader.recommendations,
            forYouLoader.isColdStart,
            forYouLoader.forYouLoading,
            forYouLoader.forYouEndReached,
            forYouLoader.forYouError,
            ::ForYouStateData,
        ),
        gameRepository.getTrendingGamesFlow(),
        combine(
            selectionManager.selectedRail,
            forYouLoader.hiddenFromTrending,
            railLoader.railStates,
            ::RailStateData,
        ),
        combine(
            loading,
            refreshing,
            error,
            userMessageRes,
            combine(librarySnapshot, editingGameId, isLibrarySubmitting, ::LibraryUi),
            ::Flags,
        ),
    ) { tab, forYou, trending, railData, flags ->
        val visibleTrending = trending.filter { it.id !in railData.hidden }
        val hasAnyContent = forYou.recommendations.isNotEmpty() ||
            visibleTrending.isNotEmpty() ||
            railData.rails.any { it.games.isNotEmpty() }
        DiscoverUiState(
            selectedTab = tab,
            selectedRail = railData.selectedRail,
            recommendations = forYou.recommendations,
            isColdStart = forYou.isColdStart,
            forYouLoading = forYou.forYouLoading,
            forYouEndReached = forYou.forYouEndReached,
            forYouError = forYou.forYouError,
            trending = visibleTrending,
            rails = railData.rails,
            isLoading = flags.loading,
            isRefreshing = flags.refreshing,
            error = if (hasAnyContent) null else flags.error,
            userMessageRes = flags.userMessageRes,
            librarySnapshot = flags.library.snapshot,
            editingGameId = flags.library.editingGameId,
            isLibrarySubmitting = flags.library.isSubmitting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscoverUiState(isLoading = true),
    )

    init {
        viewModelScope.launch {
            uiState.first { !it.isInitialLoading }
            splashHold.release()
        }
        viewModelScope.launch {
            try {
                libraryRepository.getLibraryGamesFlow().collect { result ->
                    when (result) {
                        is AppResult.Success -> {
                            val games = result.data
                            librarySnapshot.value = LibrarySnapshot.Ready(
                                games.associate { it.entry.gameId to it.entry },
                            )
                            val entries = games.map { it.entry }.toSet()
                            val isInitial = lastLibraryEntries == null
                            val libraryChanged = lastLibraryEntries != entries
                            lastLibraryEntries = entries
                            if (!isInitial && !libraryChanged) return@collect
                            if (refreshing.value && !libraryChanged) return@collect
                            if (isInitial || forYouLoader.recommendations.value.isEmpty()) {
                                forYouLoader.rebuildRecommendations(rotate = false)
                            } else {
                                forYouLoader.updateLibraryRecommendations(games)
                            }
                            loading.value = false
                        }
                        is AppResult.Error -> {
                            librarySnapshot.value = LibrarySnapshot.Failed(result.error)
                            userMessageRes.value = R.string.error_library_load_failed
                            loading.value = false
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                librarySnapshot.value = LibrarySnapshot.Failed(AppError.UnknownError(error))
                userMessageRes.value = R.string.error_library_load_failed
                loading.value = false
            }
        }
        viewModelScope.launch {
            librarySeeder.seedIfEmpty()
            refreshTrending()
            railLoader.refreshRail(DiscoverRail.entries.first(), append = false) {
                userMessageRes.value = it
            }
        }
        observeNetworkReconnect()
    }

    fun selectTab(tab: DiscoverTab) {
        selectionManager.selectTab(tab)
    }

    fun selectRail(rail: DiscoverRail) {
        selectionManager.selectRail(rail) { selected ->
            if (railLoader.isRailEmpty(selected)) {
                loadMoreRail(selected)
            }
        }
    }

    fun retry() = hydrate(isUserPullToRefresh = false)

    fun refresh() = hydrate(isUserPullToRefresh = true)

    fun retryForYou() {
        forYouLoader.retryForYou()
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

    fun loadMoreForYou() {
        forYouLoader.loadMoreForYou()
    }

    fun loadMoreTrending() {
        railLoader.loadMoreTrending(
            isRefreshing = { refreshing.value },
            onUserMessage = { userMessageRes.value = it },
        )
    }

    fun loadMoreRail(rail: DiscoverRail) {
        railLoader.loadMoreRail(
            rail = rail,
            isRefreshing = { refreshing.value },
            onUserMessage = { userMessageRes.value = it },
        )
    }

    private fun hydrate(isUserPullToRefresh: Boolean) {
        railLoader.cancelJobs()
        hydrateJob?.cancel()
        forYouLoader.cancelJobs()
        hydrateJob = viewModelScope.launch { performHydrate(isUserPullToRefresh) }
    }

    private suspend fun performHydrate(isUserPullToRefresh: Boolean) {
        if (isUserPullToRefresh) refreshing.value = true
        else if (forYouLoader.recommendations.value.isEmpty()) loading.value = true
        refreshTrending()
        if (isUserPullToRefresh) {
            val selected = selectionManager.selectedRail.value
            railLoader.resetRailForRefresh(selected)
            railLoader.refreshRail(selected, append = false) { userMessageRes.value = it }
        }
        forYouLoader.rebuildRecommendations(rotate = isUserPullToRefresh)
        loading.value = false
        refreshing.value = false
    }

    private suspend fun refreshTrending() {
        val trendingError = railLoader.refreshTrending(
            hasVisibleContent = {
                forYouLoader.recommendations.value.isNotEmpty() ||
                    gameRepository.getTrendingGamesFlow().first().isNotEmpty()
            },
            onUserMessage = { userMessageRes.value = it },
        )
        error.value = trendingError
    }

    private fun observeNetworkReconnect() {
        val monitor = networkMonitor ?: return
        viewModelScope.launch {
            monitor.status.reconnects().collect {
                if (error.value != null) {
                    retry()
                    hydrateJob?.join()
                } else {
                    when {
                        forYouLoader.hasPendingRetry -> {
                            retryForYou()
                            forYouLoader.joinJobs()
                        }
                        forYouLoader.recommendations.value.isEmpty() && !forYouLoader.isColdStart.value -> {
                            forYouLoader.rebuildRecommendations(rotate = false)
                        }
                    }
                }
                railLoader.retryFailedRails(
                    isRefreshing = { refreshing.value },
                    onUserMessage = { userMessageRes.value = it },
                )
            }
        }
    }

    private data class ForYouStateData(
        val recommendations: List<DiscoverRecommendation>,
        val isColdStart: Boolean,
        val forYouLoading: Boolean,
        val forYouEndReached: Boolean,
        val forYouError: AppError?,
    )

    private data class RailStateData(
        val selectedRail: DiscoverRail,
        val hidden: Set<Long>,
        val rails: List<DiscoverRailState>,
    )

    private data class LibraryUi(
        val snapshot: LibrarySnapshot,
        val editingGameId: Long?,
        val isSubmitting: Boolean,
    )

    private data class Flags(
        val loading: Boolean,
        val refreshing: Boolean,
        val error: AppError?,
        val userMessageRes: Int?,
        val library: LibraryUi,
    )
}
