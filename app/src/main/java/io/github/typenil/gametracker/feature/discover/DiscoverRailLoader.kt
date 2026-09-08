package io.github.typenil.gametracker.feature.discover

import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TRENDING_PAGE = 20
private const val TRENDING_CAP = 50
private const val RAIL_PAGE_SIZE = 20

internal class DiscoverRailLoader(
    private val gameRepository: GameRepository,
    private val scope: CoroutineScope,
) {
    private val _railStates = MutableStateFlow(DiscoverRail.entries.map { DiscoverRailState(it) })
    val railStates: StateFlow<List<DiscoverRailState>> = _railStates.asStateFlow()

    private val railOffsets = DiscoverRail.entries.associateWith { 0 }.toMutableMap()
    private val railJobs = mutableMapOf<DiscoverRail, Job>()
    private val trendingMutex = Mutex()
    private var appendJob: Job? = null
    private var trendingEndReached = false
    private var trendingNextOffset: Int? = 0

    init {
        DiscoverRail.entries.forEach { rail ->
            scope.launch {
                gameRepository.getPopularGamesFlow(rail.type)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        if (error !is Exception) throw error
                        updateRail(rail) { it.copy(error = AppError.UnknownError(error)) }
                    }
                    .collect { games ->
                        updateRail(rail) { it.copy(games = games) }
                    }
            }
        }
    }

    fun isRailEmpty(rail: DiscoverRail): Boolean {
        return _railStates.value.firstOrNull { it.rail == rail }?.games?.isEmpty() == true
    }

    fun loadMoreTrending(isRefreshing: () -> Boolean, onUserMessage: (Int) -> Unit) {
        if (appendJob?.isActive == true || trendingEndReached || isRefreshing()) return
        appendJob = scope.launch {
            trendingMutex.withLock {
                if (trendingEndReached || isRefreshing()) return@withLock
                val offset = trendingNextOffset ?: return@withLock
                if (offset >= TRENDING_CAP) {
                    trendingEndReached = true
                    return@withLock
                }
                val pageSize = minOf(TRENDING_PAGE, TRENDING_CAP - offset)
                when (val result = gameRepository.refreshTrendingGames(pageSize, offset, append = true)) {
                    is AppResult.Success -> {
                        val continuation = result.data
                        trendingNextOffset = continuation.nextOffset
                        val localSize = gameRepository.getTrendingGamesFlow().first().size
                        trendingEndReached = continuation.endReached || localSize >= TRENDING_CAP
                    }
                    is AppResult.Error -> onUserMessage(R.string.error_refresh_failed)
                }
            }
        }
    }

    fun loadMoreRail(rail: DiscoverRail, isRefreshing: () -> Boolean, onUserMessage: (Int) -> Unit) {
        if (railJobs[rail]?.isActive == true || isRefreshing()) return
        val offset = railOffsets.getValue(rail)
        if (_railStates.value.first { it.rail == rail }.endReached) return
        railJobs[rail] = scope.launch { refreshRail(rail, append = offset > 0, onUserMessage = onUserMessage) }
    }

    suspend fun refreshTrending(
        hasVisibleContent: suspend () -> Boolean,
        onUserMessage: (Int) -> Unit,
    ): AppError? {
        return trendingMutex.withLock {
            trendingEndReached = false
            trendingNextOffset = 0
            when (val result = gameRepository.refreshTrendingGames()) {
                is AppResult.Success -> {
                    val continuation = result.data
                    trendingNextOffset = continuation.nextOffset
                    val size = gameRepository.getTrendingGamesFlow().first().size
                    trendingEndReached = continuation.endReached || size >= TRENDING_CAP
                    null
                }
                is AppResult.Error -> {
                    if (hasVisibleContent()) {
                        onUserMessage(R.string.error_refresh_failed)
                    }
                    result.error
                }
            }
        }
    }

    suspend fun refreshRail(
        rail: DiscoverRail,
        append: Boolean,
        onUserMessage: (Int) -> Unit,
    ) {
        val offset = if (append) railOffsets.getValue(rail) else 0
        updateRail(rail) { it.copy(isLoading = true, error = null) }
        try {
            when (val result = gameRepository.refreshPopular(rail.type, RAIL_PAGE_SIZE, offset, append)) {
                is AppResult.Success -> {
                    val continuation = result.data
                    railOffsets[rail] = continuation.nextOffset ?: 0
                    updateRail(rail) {
                        it.copy(
                            isLoading = false,
                            endReached = continuation.endReached,
                            error = null,
                        )
                    }
                }
                is AppResult.Error -> {
                    updateRail(rail) { it.copy(isLoading = false, error = result.error) }
                    onUserMessage(R.string.error_refresh_failed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateRail(rail) { it.copy(isLoading = false, error = AppError.UnknownError(e)) }
        }
    }

    fun resetRailForRefresh(rail: DiscoverRail) {
        DiscoverRail.entries.forEach { railOffsets[it] = 0 }
        updateRail(rail) { it.copy(endReached = false, error = null) }
    }

    fun cancelJobs() {
        appendJob?.cancel()
        railJobs.values.forEach { it.cancel() }
        railJobs.clear()
    }

    fun retryFailedRails(isRefreshing: () -> Boolean, onUserMessage: (Int) -> Unit) {
        _railStates.value
            .filter { it.error != null }
            .forEach { failedRail ->
                loadMoreRail(failedRail.rail, isRefreshing, onUserMessage)
            }
    }

    private fun updateRail(rail: DiscoverRail, transform: (DiscoverRailState) -> DiscoverRailState) {
        _railStates.value = _railStates.value.map { if (it.rail == rail) transform(it) else it }
    }
}
