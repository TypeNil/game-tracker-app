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
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationProfile
import io.github.typenil.gametracker.core.model.RecommendationProfileBuilder
import io.github.typenil.gametracker.core.model.RecommendationSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val librarySeeder: LibrarySeeder,
    private val signalCollector: RoomRecommendationSignalCollector,
) : ViewModel() {
    private val selectedTab = MutableStateFlow(DiscoverTab.FOR_YOU)
    private val recommendations = MutableStateFlow<List<DiscoverRecommendation>>(emptyList())
    private val isColdStart = MutableStateFlow(false)
    private val forYouLoading = MutableStateFlow(false)
    private val forYouEndReached = MutableStateFlow(false)
    private var forYouSortIndex = 0
    private var forYouCurrentOffset: Int? = 0
    private val forYouJobMutex = Mutex()
    private var forYouJob: Job? = null
    private val hiddenFromTrending = MutableStateFlow<Set<Long>>(emptySet())
    private val railStates = MutableStateFlow(DiscoverRail.entries.map { DiscoverRailState(it) })
    private val railOffsets = DiscoverRail.entries.associateWith { 0 }.toMutableMap()
    private val railJobs = mutableMapOf<DiscoverRail, Job>()
    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)
    private val userMessageRes = MutableStateFlow<Int?>(null)
    private val lastShownRecIds = MutableStateFlow<Set<Long>>(emptySet())
    private var lastLibraryEntries: Set<LibraryEntry>? = null
    private val rebuildMutex = Mutex()
    private val trendingMutex = Mutex()
    private var hydrateJob: Job? = null
    private var appendJob: Job? = null
    private var trendingEndReached = false

    val uiState: StateFlow<DiscoverUiState> = combine(
        selectedTab,
        combine(recommendations, isColdStart, forYouLoading, forYouEndReached, ::ForYouStateData),
        gameRepository.getTrendingGamesFlow(),
        combine(hiddenFromTrending, railStates, ::RailStateData),
        combine(loading, refreshing, error, userMessageRes, ::Flags),
    ) { tab, forYou, trending, railData, flags ->
        val visibleTrending = trending.filter { it.id !in railData.hidden }
        val hasAnyContent = forYou.recommendations.isNotEmpty() ||
            visibleTrending.isNotEmpty() ||
            railData.rails.any { it.games.isNotEmpty() }
        DiscoverUiState(
            selectedTab = tab,
            recommendations = forYou.recommendations,
            isColdStart = forYou.isColdStart,
            forYouLoading = forYou.forYouLoading,
            forYouEndReached = forYou.forYouEndReached,
            trending = visibleTrending,
            rails = railData.rails,
            isLoading = flags.loading,
            isRefreshing = flags.refreshing,
            error = if (hasAnyContent) null else flags.error,
            userMessageRes = flags.userMessageRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscoverUiState(isLoading = true),
    )

    init {
        viewModelScope.launch {
            librarySeeder.seedIfEmpty()
            loading.value = true
            refreshTrending()
            libraryRepository.getLibraryGamesFlow().collect { games ->
                val entries = games.map { it.entry }.toSet()
                val isInitial = lastLibraryEntries == null
                val libraryChanged = lastLibraryEntries != entries
                lastLibraryEntries = entries
                if (!isInitial && !libraryChanged) return@collect
                if (refreshing.value && !libraryChanged) return@collect
                rebuildRecommendations(rotate = false)
                loading.value = false
            }
        }
    }
    fun selectTab(tab: DiscoverTab) {
        selectedTab.value = tab
    }

    fun retry() = hydrate(isUserPullToRefresh = false)

    fun refresh() = hydrate(isUserPullToRefresh = true)

    fun onUserMessageShown() {
        userMessageRes.value = null
    }

    fun loadMoreForYou() {
        if (!canLoadMoreForYou()) return
        val offset = forYouCurrentOffset ?: return
        forYouJob = viewModelScope.launch {
            forYouJobMutex.withLock {
                if (!canLoadMoreForYou()) return@withLock
                executeLoadMoreForYou(offset)
            }
        }
    }

    private fun canLoadMoreForYou(): Boolean {
        if (forYouJob?.isActive == true || forYouEndReached.value) return false
        return !refreshing.value && !isColdStart.value
    }

    private suspend fun executeLoadMoreForYou(initialOffset: Int) {
        forYouLoading.value = true
        var currentOffset: Int? = initialOffset
        while (currentOffset != null && !forYouEndReached.value) {
            val stepResult = fetchAndProcessCandidatesPage(currentOffset) ?: break
            currentOffset = stepResult.nextOffset
            if (stepResult.hasNewItems) break
        }
        forYouLoading.value = false
    }

    private suspend fun fetchAndProcessCandidatesPage(offset: Int): StepResult? {
        val signals = signalCollector.collect()
        val profile = RecommendationProfileBuilder.build(signals)
        if (profile.isColdStart) {
            isColdStart.value = true
            return null
        }
        val inLibraryIds = signals.map { it.gameId }.toSet()
        val alreadyShownIds = recommendations.value.map { it.game.id }.toSet()
        val librarySeeds = DiscoverFeedAssembler.similarSeedIds(signals, limit = 10)
        val recentSeeds = recommendations.value.takeLast(5).map { it.game.id }
        val similarSeeds = (recentSeeds + librarySeeds).distinct().take(10)
        val currentSort = FOR_YOU_SORT_MODES.getOrElse(forYouSortIndex) { FOR_YOU_SORT_MODES.first() }

        return when (val result = gameRepository.getRecommendationCandidatesPage(
            genres = DiscoverFeedAssembler.topPositiveTags(profile.genreWeights),
            themes = DiscoverFeedAssembler.topPositiveTags(profile.themeWeights),
            platforms = DiscoverFeedAssembler.topPositiveTags(profile.platformWeights),
            exclude = inLibraryIds.take(MAX_EXCLUDE_IDS).toSet(),
            similarTo = similarSeeds,
            limit = CANDIDATE_PAGE_SIZE,
            offset = offset,
            sort = currentSort,
        )) {
            is AppResult.Success -> processPageSuccess(result.data, profile, inLibraryIds, alreadyShownIds)
            is AppResult.Error -> {
                userMessageRes.value = R.string.error_refresh_failed
                null
            }
        }
    }

    private fun processPageSuccess(
        page: io.github.typenil.gametracker.core.model.RecommendationCandidatePage,
        profile: RecommendationProfile,
        inLibraryIds: Set<Long>,
        alreadyShownIds: Set<Long>,
    ): StepResult {
        val newFeed = DiscoverFeedAssembler.assemble(
            profile = profile,
            candidates = page.items,
            trending = emptyList(),
            nowEpochSeconds = System.currentTimeMillis() / 1000,
            inLibraryIds = inLibraryIds,
            shownIds = alreadyShownIds,
            pageSize = Int.MAX_VALUE,
        )
        val distinctNewRecs = newFeed.recommendations.filter { it.game.id !in alreadyShownIds }
        if (distinctNewRecs.isNotEmpty()) {
            recommendations.value = recommendations.value + distinctNewRecs
        }

        val nextOffset: Int?
        if (page.endReached || (page.nextOffset == null && page.items.isEmpty())) {
            forYouSortIndex++
            if (forYouSortIndex >= FOR_YOU_SORT_MODES.size) {
                forYouEndReached.value = true
                forYouCurrentOffset = null
                nextOffset = null
            } else {
                forYouCurrentOffset = 0
                nextOffset = 0
            }
        } else {
            forYouCurrentOffset = page.nextOffset
            nextOffset = page.nextOffset
        }
        return StepResult(nextOffset = nextOffset, hasNewItems = distinctNewRecs.isNotEmpty())
    }
    fun loadMoreTrending() {
        if (appendJob?.isActive == true || trendingEndReached || refreshing.value) return
        appendJob = viewModelScope.launch {
            trendingMutex.withLock {
                if (trendingEndReached || refreshing.value) return@withLock
                val offset = gameRepository.getTrendingGamesFlow().first().size
                if (offset == 0 || offset >= TRENDING_CAP) {
                    trendingEndReached = offset >= TRENDING_CAP
                    return@withLock
                }
                val pageSize = minOf(TRENDING_PAGE, TRENDING_CAP - offset)
                when (val result = gameRepository.refreshTrendingGames(pageSize, offset, append = true)) {
                    is AppResult.Success -> {
                        val newSize = gameRepository.getTrendingGamesFlow().first().size
                        if (newSize <= offset || newSize >= TRENDING_CAP || newSize - offset < pageSize) {
                            trendingEndReached = true
                        }
                    }
                    is AppResult.Error -> userMessageRes.value = R.string.error_refresh_failed
                }
            }
        }
    }

    fun loadMoreRail(rail: DiscoverRail) {
        if (railJobs[rail]?.isActive == true || refreshing.value) return
        val offset = railOffsets.getValue(rail)
        if (railStates.value.first { it.rail == rail }.endReached) return
        railJobs[rail] = viewModelScope.launch { refreshRail(rail, append = offset > 0) }
    }

    private fun hydrate(isUserPullToRefresh: Boolean) {
        appendJob?.cancel()
        hydrateJob?.cancel()
        forYouJob?.cancel()
        hydrateJob = viewModelScope.launch { performHydrate(isUserPullToRefresh) }
    }
    private suspend fun performHydrate(isUserPullToRefresh: Boolean) {
        if (isUserPullToRefresh) refreshing.value = true
        else if (recommendations.value.isEmpty()) loading.value = true
        refreshTrending()
        if (isUserPullToRefresh) {
            val loadedRails = DiscoverRail.entries.filter { rail ->
                railStates.value.first { it.rail == rail }.games.isNotEmpty()
            }
            loadedRails.forEach { railOffsets[it] = 0 }
            loadedRails.forEach { refreshRail(it, append = false) }
        }
        rebuildRecommendations(rotate = isUserPullToRefresh)
        loading.value = false
        refreshing.value = false
    }
    private suspend fun refreshRail(rail: DiscoverRail, append: Boolean) {
        val offset = if (append) railOffsets.getValue(rail) else 0
        updateRail(rail) { it.copy(isLoading = true) }
        when (gameRepository.refreshPopular(rail.type, RAIL_PAGE_SIZE, offset, append)) {
            is AppResult.Success -> {
                val games = gameRepository.getPopularGamesFlow(rail.type).first()
                railOffsets[rail] = games.size
                updateRail(rail) {
                    it.copy(
                        games = games,
                        isLoading = false,
                        endReached = games.size < offset + RAIL_PAGE_SIZE,
                    )
                }
            }
            is AppResult.Error -> {
                updateRail(rail) { it.copy(isLoading = false) }
                userMessageRes.value = R.string.error_refresh_failed
            }
        }
    }

    private suspend fun refreshTrending() {
        trendingMutex.withLock {
            trendingEndReached = false
            when (val result = gameRepository.refreshTrendingGames()) {
                is AppResult.Success -> {
                    error.value = null
                    val size = gameRepository.getTrendingGamesFlow().first().size
                    if (size < TRENDING_PAGE || size >= TRENDING_CAP) trendingEndReached = true
                }
                is AppResult.Error -> {
                    error.value = result.error
                    if (recommendations.value.isNotEmpty() || gameRepository.getTrendingGamesFlow().first().isNotEmpty()) {
                        userMessageRes.value = R.string.error_refresh_failed
                    }
                }
            }
        }
    }

    private fun updateRail(rail: DiscoverRail, transform: (DiscoverRailState) -> DiscoverRailState) {
        railStates.value = railStates.value.map { if (it.rail == rail) transform(it) else it }
    }

    private suspend fun rebuildRecommendations(rotate: Boolean) {
        rebuildMutex.withLock {
            forYouJob?.cancel()
            val signals = signalCollector.collect()
            val profile = RecommendationProfileBuilder.build(signals)
            isColdStart.value = profile.isColdStart
            forYouSortIndex = 0
            forYouCurrentOffset = 0
            forYouEndReached.value = false
            val inLibraryIds = signals.map { it.gameId }.toSet()
            val candidates = if (profile.isColdStart) emptyList() else fetchCandidates(profile, signals, inLibraryIds)
            val shownIds = if (rotate) lastShownRecIds.value else emptySet()
            val feed = DiscoverFeedAssembler.assemble(
                profile = profile,
                candidates = candidates,
                trending = emptyList(),
                nowEpochSeconds = System.currentTimeMillis() / 1000,
                inLibraryIds = inLibraryIds,
                shownIds = shownIds,
                pageSize = Int.MAX_VALUE,
            )
            recommendations.value = feed.recommendations
            lastShownRecIds.value = feed.recommendations.map { it.game.id }.toSet()
            hiddenFromTrending.value = lastShownRecIds.value + profile.excludedGameIds
            forYouCurrentOffset = candidates.size.takeIf { it > 0 } ?: 0
        }
    }

    private suspend fun fetchCandidates(
        profile: RecommendationProfile,
        signals: List<RecommendationSignal>,
        inLibraryIds: Set<Long>,
    ): List<RecommendationCandidate> {
        return when (val result = gameRepository.getRecommendationCandidates(
            genres = DiscoverFeedAssembler.topPositiveTags(profile.genreWeights),
            themes = DiscoverFeedAssembler.topPositiveTags(profile.themeWeights),
            platforms = DiscoverFeedAssembler.topPositiveTags(profile.platformWeights),
            exclude = inLibraryIds,
            similarTo = DiscoverFeedAssembler.similarSeedIds(signals),
        )) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                userMessageRes.value = R.string.error_refresh_failed
                emptyList()
            }
        }
    }

    private data class StepResult(val nextOffset: Int?, val hasNewItems: Boolean)
    private data class ForYouStateData(
        val recommendations: List<DiscoverRecommendation>,
        val isColdStart: Boolean,
        val forYouLoading: Boolean,
        val forYouEndReached: Boolean,
    )

    private data class RailStateData(
        val hidden: Set<Long>,
        val rails: List<DiscoverRailState>,
    )

    private data class Flags(
        val loading: Boolean,
        val refreshing: Boolean,
        val error: AppError?,
        val userMessageRes: Int?,
    )
}

private val FOR_YOU_SORT_MODES = listOf("follows", "hypes", "first_release_date")
private const val CANDIDATE_PAGE_SIZE = 30
private const val MAX_EXCLUDE_IDS = 50
private const val TRENDING_PAGE = 20
private const val TRENDING_CAP = 50
private const val RAIL_PAGE_SIZE = 20
