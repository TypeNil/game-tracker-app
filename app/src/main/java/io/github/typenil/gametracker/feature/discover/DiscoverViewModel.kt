package io.github.typenil.gametracker.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.recommendations.DiscoverFeedAssembler
import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import io.github.typenil.gametracker.core.data.recommendations.RoomRecommendationSignalCollector
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationProfile
import io.github.typenil.gametracker.core.model.RecommendationProfileBuilder
import io.github.typenil.gametracker.core.model.RecommendationSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val librarySeeder: LibrarySeeder,
    private val signalCollector: RoomRecommendationSignalCollector,
) : ViewModel() {

    private val recommendations = MutableStateFlow<List<DiscoverRecommendation>>(emptyList())
    private val hiddenFromTrending = MutableStateFlow<Set<Long>>(emptySet())
    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val userMessageRes = MutableStateFlow<Int?>(null)
    private val rebuildMutex = Mutex()

    val uiState: StateFlow<DiscoverUiState> = combine(
        recommendations,
        gameRepository.getTrendingGamesFlow(),
        hiddenFromTrending,
        combine(loading, refreshing, error, userMessageRes, ::Flags),
    ) { recs, trending, hidden, flags ->
        val visibleTrending = trending.filter { it.id !in hidden }
        val hasContent = recs.isNotEmpty() || visibleTrending.isNotEmpty()
        DiscoverUiState(
            recommendations = recs,
            trending = visibleTrending,
            isLoading = flags.loading,
            isRefreshing = flags.refreshing,
            error = if (hasContent) null else flags.error,
            userMessageRes = flags.userMessageRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscoverUiState(isLoading = true),
    )

    private var hydrateJob: Job? = null

    init {
        viewModelScope.launch {
            librarySeeder.seedIfEmpty()
            loading.value = true
            refreshTrending()
            libraryRepository.getLibraryGamesFlow().collect {
                rebuildRecommendations()
                loading.value = false
            }
        }
    }

    fun retry() {
        hydrate(isUserPullToRefresh = false)
    }

    fun refresh() {
        hydrate(isUserPullToRefresh = true)
    }

    fun onUserMessageShown() {
        userMessageRes.value = null
    }

    private fun hydrate(isUserPullToRefresh: Boolean) {
        hydrateJob?.cancel()
        hydrateJob = viewModelScope.launch {
            performHydrate(isUserPullToRefresh)
        }
    }

    private suspend fun performHydrate(isUserPullToRefresh: Boolean) {
        if (isUserPullToRefresh) {
            refreshing.value = true
        } else if (recommendations.value.isEmpty()) {
            loading.value = true
        }
        refreshTrending()
        rebuildRecommendations()
        loading.value = false
        refreshing.value = false
    }

    private suspend fun refreshTrending() {
        when (val result = gameRepository.refreshTrendingGames()) {
            is AppResult.Success -> {
                error.value = null
            }
            is AppResult.Error -> {
                error.value = result.error
                if (recommendations.value.isNotEmpty()) {
                    userMessageRes.value = R.string.error_refresh_failed
                }
            }
        }
    }

    private suspend fun rebuildRecommendations() {
        rebuildMutex.withLock {
            val signals = signalCollector.collect()
            val profile = RecommendationProfileBuilder.build(signals)
            val inLibraryIds = signals.map { it.gameId }.toSet()
            val candidates = if (profile.isColdStart) {
                emptyList()
            } else {
                fetchCandidates(profile, signals, inLibraryIds)
            }
            val feed = DiscoverFeedAssembler.assemble(
                profile = profile,
                candidates = candidates,
                trending = emptyList(),
                nowEpochSeconds = System.currentTimeMillis() / 1000,
                inLibraryIds = inLibraryIds,
            )
            recommendations.value = feed.recommendations
            hiddenFromTrending.value = feed.recommendations.map { it.game.id }.toSet() + profile.excludedGameIds
        }
    }

    private suspend fun fetchCandidates(
        profile: RecommendationProfile,
        signals: List<RecommendationSignal>,
        inLibraryIds: Set<Long>,
    ): List<RecommendationCandidate> {
        return when (
            val result = gameRepository.getRecommendationCandidates(
                genres = DiscoverFeedAssembler.topPositiveTags(profile.genreWeights),
                themes = DiscoverFeedAssembler.topPositiveTags(profile.themeWeights),
                platforms = DiscoverFeedAssembler.topPositiveTags(profile.platformWeights),
                exclude = inLibraryIds,
                similarTo = DiscoverFeedAssembler.similarSeedIds(signals),
            )
        ) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                userMessageRes.value = R.string.error_refresh_failed
                emptyList()
            }
        }
    }

    private data class Flags(
        val loading: Boolean,
        val refreshing: Boolean,
        val error: AppError?,
        val userMessageRes: Int?,
    )
}
