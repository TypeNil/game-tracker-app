package io.github.typenil.gametracker.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.connectivity.reconnects
import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.model.UserPreferences
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibrarySnapshot
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val librarySeeder: LibrarySeeder,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val networkMonitor: NetworkMonitor? = null,
) : ViewModel() {

    private val selectionManager = DiscoverSelectionManager()
    private val railLoader = DiscoverRailLoader(gameRepository, viewModelScope)
    private val userPreferences = MutableStateFlow(UserPreferences())
    private val forYouLoader = DiscoverForYouLoader(
        gameRepository = gameRepository,
        libraryRepository = libraryRepository,
        scope = viewModelScope,
        onUserMessage = { userMessageRes.value = it },
        isRefreshing = { refreshing.value },
        userPreferences = { userPreferences.value },
    )

    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
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
        combine(
            selectionManager.selectedRail,
            railLoader.railStates,
            ::RailStateData,
        ),
        combine(
            loading,
            refreshing,
            userMessageRes,
            combine(librarySnapshot, editingGameId, isLibrarySubmitting, ::LibraryUi),
            ::Flags,
        ),
        userPreferences,
    ) { tab, forYou, railData, flags, prefs ->
        DiscoverUiState(
            selectedTab = tab,
            selectedRail = railData.selectedRail,
            recommendations = forYou.recommendations,
            isColdStart = forYou.isColdStart,
            forYouLoading = forYou.forYouLoading,
            forYouEndReached = forYou.forYouEndReached,
            forYouError = forYou.forYouError,
            rails = railData.rails,
            isLoading = flags.loading,
            isRefreshing = flags.refreshing,
            userMessageRes = flags.userMessageRes,
            librarySnapshot = flags.library.snapshot,
            editingGameId = flags.library.editingGameId,
            isLibrarySubmitting = flags.library.isSubmitting,
            recommendationGenres = prefs.recommendationGenres,
            recommendationPlatforms = prefs.recommendationPlatforms,
            recommendationOnboardingDismissed = prefs.recommendationOnboardingDismissed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscoverUiState(isLoading = true),
    )

    init {
        viewModelScope.launch {
            try {
                combine(
                    libraryRepository.getLibraryGamesFlow(),
                    userPreferencesRepository.preferences.distinctUntilChanged(),
                ) { libraryResult, preferences -> libraryResult to preferences }
                    .collectLatest { (libraryResult, preferences) ->
                        val prefsChanged = userPreferences.value != preferences
                        userPreferences.value = preferences
                        handleLibraryResult(libraryResult, forceRebuild = prefsChanged)
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
            railLoader.refreshRail(DiscoverRail.entries.first(), append = false) {
                userMessageRes.value = it
            }
        }
        observeNetworkReconnect()
    }

    private suspend fun handleLibraryResult(
        result: AppResult<List<LibraryGame>>,
        forceRebuild: Boolean,
    ) {
        when (result) {
            is AppResult.Success -> {
                val games = result.data
                librarySnapshot.value = LibrarySnapshot.Ready(
                    games.associate { it.entry.gameId to it.entry },
                )
                val entries = games.map { it.entry }.toSet()
                val isInitial = lastLibraryEntries == null
                val libraryChanged = lastLibraryEntries != entries
                if (!isInitial && !libraryChanged && !forceRebuild) return
                if (refreshing.value && !libraryChanged && !forceRebuild) return
                if (forceRebuild || isInitial || forYouLoader.recommendations.value.isEmpty()) {
                    forYouLoader.rebuildRecommendations(rotate = false)
                } else {
                    forYouLoader.updateLibraryRecommendations(games)
                }
                lastLibraryEntries = entries
                loading.value = false
            }
            is AppResult.Error -> {
                librarySnapshot.value = LibrarySnapshot.Failed(result.error)
                userMessageRes.value = R.string.error_library_load_failed
                loading.value = false
            }
        }
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

    fun refresh() = hydrate()

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

    suspend fun saveRecommendationPreferences(genres: Set<String>, platforms: Set<String>): Boolean {
        return when (val result = userPreferencesRepository.setRecommendationPreferences(genres, platforms)) {
            is AppResult.Success -> true
            is AppResult.Error -> {
                userMessageRes.value = R.string.error_preferences_save_failed
                false
            }
        }
    }

    suspend fun skipRecommendationOnboarding(): Boolean {
        return when (userPreferencesRepository.skipRecommendationOnboarding()) {
            is AppResult.Success -> true
            is AppResult.Error -> {
                userMessageRes.value = R.string.error_preferences_save_failed
                false
            }
        }
    }


    fun loadMoreRail(rail: DiscoverRail) {
        railLoader.loadMoreRail(
            rail = rail,
            isRefreshing = { refreshing.value },
            onUserMessage = { userMessageRes.value = it },
        )
    }

    private fun hydrate() {
        railLoader.cancelJobs()
        hydrateJob?.cancel()
        forYouLoader.cancelJobs()
        hydrateJob = viewModelScope.launch { performRefresh() }
    }

    private suspend fun performRefresh() {
        refreshing.value = true
        val selected = selectionManager.selectedRail.value
        railLoader.resetRailForRefresh(selected)
        railLoader.refreshRail(selected, append = false) { userMessageRes.value = it }
        forYouLoader.rebuildRecommendations(rotate = true)
        loading.value = false
        refreshing.value = false
    }

    private fun observeNetworkReconnect() {
        val monitor = networkMonitor ?: return
        viewModelScope.launch {
            monitor.status.reconnects().collect {
                when {
                    forYouLoader.hasPendingRetry -> {
                        retryForYou()
                        forYouLoader.joinJobs()
                    }
                    forYouLoader.recommendations.value.isEmpty() && !forYouLoader.isColdStart.value -> {
                        forYouLoader.rebuildRecommendations(rotate = false)
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
        val userMessageRes: Int?,
        val library: LibraryUi,
    )
}
