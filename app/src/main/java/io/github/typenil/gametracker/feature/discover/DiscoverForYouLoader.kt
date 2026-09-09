package io.github.typenil.gametracker.feature.discover

import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.recommendations.DiscoverFeed
import io.github.typenil.gametracker.core.data.recommendations.DiscoverFeedAssembler
import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.RecommendationCandidatePage
import io.github.typenil.gametracker.core.model.RecommendationProfile
import io.github.typenil.gametracker.core.model.RecommendationProfileBuilder
import io.github.typenil.gametracker.core.model.RecommendationSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val FOR_YOU_SORT_MODES = listOf("follows", "hypes", "first_release_date")
private const val CANDIDATE_PAGE_SIZE = 30
private const val MAX_EXCLUDE_IDS = 50

@Suppress("TooManyFunctions")
internal class DiscoverForYouLoader(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
    private val onUserMessage: (Int) -> Unit,
    private val isRefreshing: () -> Boolean,
) {
    private val _recommendations = MutableStateFlow<List<DiscoverRecommendation>>(emptyList())
    val recommendations: StateFlow<List<DiscoverRecommendation>> = _recommendations.asStateFlow()

    private val _isColdStart = MutableStateFlow(false)
    val isColdStart: StateFlow<Boolean> = _isColdStart.asStateFlow()

    private val _forYouLoading = MutableStateFlow(false)
    val forYouLoading: StateFlow<Boolean> = _forYouLoading.asStateFlow()

    private val _forYouEndReached = MutableStateFlow(false)
    val forYouEndReached: StateFlow<Boolean> = _forYouEndReached.asStateFlow()

    private val _forYouError = MutableStateFlow<AppError?>(null)
    val forYouError: StateFlow<AppError?> = _forYouError.asStateFlow()


    private var lastShownRecIds: Set<Long> = emptySet()
    private var forYouSortIndex = 0
    private var forYouCurrentOffset: Int? = 0
    private val forYouJobMutex = Mutex()
    private var forYouJob: Job? = null
    private var pendingForYouRetry: ForYouRetry? = null
    private var forYouRetryJob: Job? = null
    private val rebuildMutex = Mutex()

    val hasPendingRetry: Boolean
        get() = pendingForYouRetry != null

    fun loadMoreForYou() {
        if (!canStartLoadMoreForYou()) return
        val offset = forYouCurrentOffset ?: return
        forYouJob = scope.launch {
            forYouJobMutex.withLock {
                if (!canExecuteLoadMoreForYou()) return@withLock
                executeLoadMoreForYou(offset)
            }
        }
    }

    private fun canStartLoadMoreForYou(): Boolean {
        if (forYouJob?.isActive == true) return false
        return canExecuteLoadMoreForYou()
    }

    private fun canExecuteLoadMoreForYou(): Boolean {
        if (_forYouEndReached.value) return false
        if (_forYouError.value != null) return false
        return !isRefreshing() && !_isColdStart.value
    }

    private suspend fun executeLoadMoreForYou(initialOffset: Int) {
        _forYouLoading.value = true
        try {
            var currentOffset: Int? = initialOffset
            while (currentOffset != null && !_forYouEndReached.value) {
                val transition = fetchAndProcessCandidatesPage(currentOffset) ?: break
                currentOffset = transition.nextOffset
                if (transition.hasNewItems) break
            }
        } finally {
            _forYouLoading.value = false
        }
    }

    private suspend fun fetchAndProcessCandidatesPage(offset: Int): ForYouTransition? {
        val signals = loadRecommendationSignals(ForYouRetry.Append) ?: return null
        val profile = RecommendationProfileBuilder.build(signals)
        if (profile.isColdStart) {
            _isColdStart.value = true
            return null
        }
        val librarySeeds = DiscoverFeedAssembler.similarSeedIds(signals, limit = 10)
        val currentSort = FOR_YOU_SORT_MODES.getOrElse(forYouSortIndex) { FOR_YOU_SORT_MODES.first() }
        val excludeIds = signals.map { it.gameId }.take(MAX_EXCLUDE_IDS).toSet()

        return when (val result = gameRepository.getRecommendationCandidatesPage(
            genres = DiscoverFeedAssembler.topPositiveTags(profile.genreWeights),
            themes = DiscoverFeedAssembler.topPositiveTags(profile.themeWeights),
            platforms = DiscoverFeedAssembler.topPositiveTags(profile.platformWeights),
            exclude = excludeIds,
            similarTo = librarySeeds,
            limit = CANDIDATE_PAGE_SIZE,
            offset = offset,
            sort = currentSort,
        )) {
            is AppResult.Success -> {
                pendingForYouRetry = null
                _forYouError.value = null
                val currentSignals = loadRecommendationSignals(ForYouRetry.Append) ?: return null
                val inLibraryIds = currentSignals.map { it.gameId }.toSet()
                val input = ForYouPageInput(
                    currentRecommendations = _recommendations.value,
                    inLibraryIds = inLibraryIds,
                    page = result.data,
                    profile = profile,
                    sortIndex = forYouSortIndex,
                    sortModeCount = FOR_YOU_SORT_MODES.size,
                )
                val transition = reduceForYouAppend(input)
                applyForYouAppendTransition(transition)
                transition
            }
            is AppResult.Error -> {
                exposeForYouFailure(result.error, ForYouRetry.Append)
                null
            }
        }
    }

    private fun applyForYouAppendTransition(
        transition: ForYouTransition,
    ) {
        val recIds = transition.recommendations.map { it.game.id }.toSet()
        lastShownRecIds = recIds
        _recommendations.value = transition.recommendations
        forYouSortIndex = transition.nextSortIndex
        forYouCurrentOffset = transition.nextOffset
        _forYouEndReached.value = transition.endReached
    }

    suspend fun rebuildRecommendations(rotate: Boolean) {
        rebuildMutex.withLock {
            forYouJob?.cancel()
            val retry = ForYouRetry.Rebuild(rotate)
            val signals = loadRecommendationSignals(retry) ?: return@withLock
            val profile = RecommendationProfileBuilder.build(signals)
            val nextSortIndex = if (rotate) {
                (forYouSortIndex + 1) % FOR_YOU_SORT_MODES.size
            } else {
                0
            }
            val inLibraryIds = signals.map { it.gameId }.toSet()
            val currentSort = FOR_YOU_SORT_MODES.getOrElse(nextSortIndex) { FOR_YOU_SORT_MODES.first() }
            if (profile.isColdStart) {
                applyColdStartRecommendations(nextSortIndex)
                return@withLock
            }
            when (
                val result = gameRepository.getRecommendationCandidatesPage(
                    genres = DiscoverFeedAssembler.topPositiveTags(profile.genreWeights),
                    themes = DiscoverFeedAssembler.topPositiveTags(profile.themeWeights),
                    platforms = DiscoverFeedAssembler.topPositiveTags(profile.platformWeights),
                    exclude = inLibraryIds.take(MAX_EXCLUDE_IDS).toSet(),
                    similarTo = DiscoverFeedAssembler.similarSeedIds(signals, limit = 10),
                    limit = CANDIDATE_PAGE_SIZE,
                    offset = 0,
                    sort = currentSort,
                )
            ) {
                is AppResult.Success -> {
                    val hasItems = applyForYouPage(
                        page = result.data,
                        profile = profile,
                        inLibraryIds = inLibraryIds,
                        rotate = rotate,
                        nextSortIndex = nextSortIndex,
                    )
                    if (!hasItems && !_forYouEndReached.value) {
                        val nextOffset = forYouCurrentOffset
                        if (nextOffset != null) {
                            executeLoadMoreForYou(nextOffset)
                        }
                    }
                }
                is AppResult.Error -> exposeForYouFailure(result.error, retry)
            }
        }
    }

    private suspend fun loadRecommendationSignals(retry: ForYouRetry): List<RecommendationSignal>? {
        return when (val result = libraryRepository.getRecommendationSignals()) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                exposeForYouFailure(result.error, retry)
                null
            }
        }
    }

    private fun exposeForYouFailure(error: AppError, retry: ForYouRetry) {
        pendingForYouRetry = retry
        _forYouError.value = error
        onUserMessage(R.string.error_refresh_failed)
    }

    private fun applyColdStartRecommendations(nextSortIndex: Int) {
        pendingForYouRetry = null
        _isColdStart.value = true
        _forYouError.value = null
        forYouSortIndex = nextSortIndex
        forYouCurrentOffset = 0
        _forYouEndReached.value = true
        _recommendations.value = emptyList()
        lastShownRecIds = emptySet()
    }

    private suspend fun applyForYouPage(
        page: RecommendationCandidatePage,
        profile: RecommendationProfile,
        inLibraryIds: Set<Long>,
        rotate: Boolean,
        nextSortIndex: Int,
    ): Boolean {
        val shownIds = if (rotate) lastShownRecIds else emptySet()
        val currentSignals = loadRecommendationSignals(ForYouRetry.Rebuild(rotate)) ?: return false
        val currentInLibraryIds = currentSignals.map { it.gameId }.toSet()
        val feed = reduceForYouRebuild(
            profile = profile,
            page = page,
            inLibraryIds = currentInLibraryIds,
            historicShownIds = shownIds,
        )
        _isColdStart.value = false
        pendingForYouRetry = null
        _forYouError.value = null
        forYouSortIndex = nextSortIndex
        _forYouEndReached.value = false

        if (page.endReached || page.nextOffset == null) {
            if (nextSortIndex + 1 >= FOR_YOU_SORT_MODES.size) {
                _forYouEndReached.value = true
                forYouCurrentOffset = null
            } else {
                forYouSortIndex = nextSortIndex + 1
                forYouCurrentOffset = 0
            }
        } else {
            forYouCurrentOffset = page.nextOffset
        }
        val recIds = feed.recommendations.map { it.game.id }.toSet()
        lastShownRecIds = recIds
        _recommendations.value = feed.recommendations
        return feed.recommendations.isNotEmpty()
    }
    suspend fun updateLibraryRecommendations(games: List<LibraryGame>) {
        rebuildMutex.withLock {
            val signals = when (val result = libraryRepository.getRecommendationSignals()) {
                is AppResult.Success -> result.data
                is AppResult.Error -> {
                    exposeForYouFailure(result.error, ForYouRetry.Rebuild(rotate = false))
                    return@withLock
                }
            }
            val profile = RecommendationProfileBuilder.build(signals)
            if (profile.isColdStart) {
                applyColdStartRecommendations(forYouSortIndex)
                return@withLock
            }
            _isColdStart.value = false
            val inLibraryIds = signals.map { it.gameId }.toSet()
            val excludedFromLibrary = games
                .filter { it.entry.status == LibraryStatus.DROPPED || it.entry.status == LibraryStatus.NOT_INTERESTED }
                .map { it.game.id }
                .toSet()
            val removedIds = inLibraryIds + excludedFromLibrary + profile.excludedGameIds
            _recommendations.value = _recommendations.value.filter { it.game.id !in removedIds }
            lastShownRecIds = _recommendations.value.map { it.game.id }.toSet()
        }
    }

    fun retryForYou() {
        val retry = pendingForYouRetry ?: return
        if (forYouRetryJob?.isActive == true) return

        _forYouError.value = null
        pendingForYouRetry = null

        when (retry) {
            ForYouRetry.Append -> loadMoreForYou()
            is ForYouRetry.Rebuild -> {
                forYouRetryJob = scope.launch {
                    _forYouLoading.value = true
                    try {
                        rebuildRecommendations(rotate = retry.rotate)
                    } finally {
                        _forYouLoading.value = false
                    }
                }
            }
        }
    }

    fun cancelJobs() {
        forYouJob?.cancel()
    }

    suspend fun joinJobs() {
        forYouRetryJob?.join()
        forYouJob?.join()
    }
}

internal sealed interface ForYouRetry {
    data class Rebuild(val rotate: Boolean) : ForYouRetry
    data object Append : ForYouRetry
}

internal data class ForYouPageInput(
    val currentRecommendations: List<DiscoverRecommendation>,
    val inLibraryIds: Set<Long>,
    val page: RecommendationCandidatePage,
    val profile: RecommendationProfile,
    val sortIndex: Int,
    val sortModeCount: Int,
)

internal data class ForYouTransition(
    val recommendations: List<DiscoverRecommendation>,
    val nextOffset: Int?,
    val nextSortIndex: Int,
    val endReached: Boolean,
    val hasNewItems: Boolean,
)

internal fun reduceForYouAppend(input: ForYouPageInput): ForYouTransition {
    val shownIds = input.currentRecommendations.map { it.game.id }.toSet()
    val newFeed = DiscoverFeedAssembler.assemble(
        profile = input.profile,
        candidates = input.page.items,
        trending = emptyList(),
        inLibraryIds = input.inLibraryIds,
        shownIds = shownIds,
        pageSize = Int.MAX_VALUE,
    )
    val distinctNewRecs = newFeed.recommendations.filter { it.game.id !in shownIds }
    val completeRecommendations = input.currentRecommendations + distinctNewRecs

    val nextOffset: Int?
    val nextSortIndex: Int
    val endReached: Boolean
    if (input.page.endReached || (input.page.nextOffset == null && input.page.items.isEmpty())) {
        val advancedSort = input.sortIndex + 1
        if (advancedSort >= input.sortModeCount) {
            nextSortIndex = advancedSort
            endReached = true
            nextOffset = null
        } else {
            nextSortIndex = advancedSort
            endReached = false
            nextOffset = 0
        }
    } else {
        nextSortIndex = input.sortIndex
        endReached = false
        nextOffset = input.page.nextOffset
    }

    return ForYouTransition(
        recommendations = completeRecommendations,
        nextOffset = nextOffset,
        nextSortIndex = nextSortIndex,
        endReached = endReached,
        hasNewItems = distinctNewRecs.isNotEmpty(),
    )
}

internal fun reduceForYouRebuild(
    profile: RecommendationProfile,
    page: RecommendationCandidatePage,
    inLibraryIds: Set<Long>,
    historicShownIds: Set<Long>,
): DiscoverFeed {
    return DiscoverFeedAssembler.assemble(
        profile = profile,
        candidates = page.items,
        trending = emptyList(),
        inLibraryIds = inLibraryIds,
        shownIds = historicShownIds,
        pageSize = Int.MAX_VALUE,
    )
}
