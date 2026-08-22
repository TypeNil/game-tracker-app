package io.github.typenil.gametracker.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameDetailsViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val gameId: Long = savedStateHandle.get<Long>(KEY_GAME_ID)
        ?: error("GameDetailsViewModel requires a '$KEY_GAME_ID' Long argument")

    private val _loading = MutableStateFlow(true)
    private val _refreshing = MutableStateFlow(false)
    // Pair of (error, userMessageRes): one container keeps the uiState combine within
    // the five-flow overload while preserving Discover's snackbar semantics.
    private val _message = MutableStateFlow<Pair<AppError?, Int?>?>(null)

    /**
     * Lazily (not WhileSubscribed): this screen pushes another copy of itself onto
     * the back stack via similar games; a 5s sharing timeout would tear the pipeline
     * down while covered and re-run it on pop. Refresh is init-triggered and TTL-gated
     * in the repository, so a warm upstream never re-fetches on return.
     */
    val uiState: StateFlow<GameDetailsUiState> = combine(
        gameRepository.getGameDetailsFlow(gameId),
        gameRepository.isGameDetailsHydratedFlow(gameId),
        _loading,
        _refreshing,
        _message
    ) { game, isHydrated, isLoading, isRefreshing, message ->
        val error = message?.first
        GameDetailsUiState(
            game = game,
            isHydrated = isHydrated,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = if (game != null) null else error,
            userMessageRes = if (game != null && error != null) {
                message?.second ?: R.string.error_refresh_failed
            } else {
                message?.second
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = GameDetailsUiState(isLoading = true)
    )

    private var refreshJob: Job? = null

    init {
        refreshDetails(force = false)
        observeEviction()
    }

    /** Pull-to-refresh. */
    fun refresh() {
        refreshDetails(force = true)
    }

    /** Retry after a failed initial load. */
    fun retry() {
        refreshDetails(force = true)
    }

    fun onUserMessageShown() {
        _message.update { it?.copy(first = null, second = null) }
    }

    private fun refreshDetails(force: Boolean) {
        // Single-flight: a refresh in progress swallows retries, PTR and the
        // eviction guard alike; they are all idempotent over fresh data.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            // Initial fetch over an existing catalog skeleton behaves like pull-to-refresh:
            // the header is already on screen, so show the refresh indicator instead of a
            // full-screen loader. One-shot repository read at refresh start (a stateIn
            // flag would still hold its initialValue before the first UI subscription).
            val asPullRefresh = gameRepository.getGameDetailsFlow(gameId).first() != null
            if (asPullRefresh) {
                _refreshing.value = true
            } else {
                _loading.value = true
                _message.value = null to null
            }

            try {
                when (val result = gameRepository.refreshGameDetails(gameId, force = force)) {
                    is AppResult.Success -> _message.value = null to null
                    is AppResult.Error -> _message.value = result.error to null
                }
            } finally {
                if (asPullRefresh) {
                    _refreshing.value = false
                } else {
                    _loading.value = false
                }
            }
        }
    }

    /**
     * Eviction guard: a hydrated details row that disappears while this VM is alive
     * (stale-cache cleanup triggered by another screen's refresh) downgrades the UI
     * to a skeleton. Declaratively detect the hydrated -> not-hydrated transition and
     * refetch once; single-flight lives in [refreshDetails].
     */
    private fun observeEviction() {
        viewModelScope.launch {
            var wasHydrated = false
            gameRepository.isGameDetailsHydratedFlow(gameId).collect { hydrated ->
                if (wasHydrated && !hydrated) {
                    refreshDetails(force = true)
                }
                wasHydrated = hydrated
            }
        }
    }

    companion object {
        const val KEY_GAME_ID = "gameId"
    }
}
