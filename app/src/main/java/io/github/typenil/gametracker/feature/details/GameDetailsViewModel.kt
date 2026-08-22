package io.github.typenil.gametracker.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.feature.details.navigation.GameDetailsKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
class GameDetailsViewModel(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    val gameId: Long
) : ViewModel() {

    @Inject
    constructor(
        gameRepository: GameRepository,
        libraryRepository: LibraryRepository,
        savedStateHandle: SavedStateHandle
    ) : this(
        gameRepository,
        libraryRepository,
        savedStateHandle.toRoute<GameDetailsKey>().gameId
    )

    private val _flags = MutableStateFlow(DetailsInternalFlags())

    /**
     * Lazily (not WhileSubscribed): this screen pushes another copy of itself onto
     * the back stack via similar games; a 5s sharing timeout would tear the pipeline
     * down while covered and re-run it on pop. Refresh is init-triggered and TTL-gated
     * in the repository, so a warm upstream never re-fetches on return.
     */
    val uiState: StateFlow<GameDetailsUiState> = combine(
        gameRepository.getGameDetailsFlow(gameId),
        gameRepository.isGameDetailsHydratedFlow(gameId),
        libraryRepository.getLibraryEntryFlow(gameId),
        _flags
    ) { game, isHydrated, libraryEntry, flags ->
        val error = flags.message?.first
        GameDetailsUiState(
            game = game,
            libraryEntry = libraryEntry,
            isHydrated = isHydrated,
            isLoading = flags.isLoading,
            isRefreshing = flags.isRefreshing,
            isEditingLibrary = flags.isEditingLibrary,
            error = if (game != null) null else error,
            userMessageRes = if (game != null && error != null) {
                flags.message?.second ?: R.string.error_refresh_failed
            } else {
                flags.message?.second
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
        refreshDetails(force = true, isUserPullRefresh = true)
    }

    /** Retry after a failed initial load. */
    fun retry() {
        refreshDetails(force = true, isUserPullRefresh = false)
    }

    fun onUserMessageShown() {
        _flags.update { it.copy(message = null) }
    }

    fun onEditLibraryClicked() {
        _flags.update { it.copy(isEditingLibrary = true) }
    }

    fun onDismissEditLibrary() {
        _flags.update { it.copy(isEditingLibrary = false) }
    }

    fun onSaveLibraryEntry(
        status: LibraryStatus,
        userRating: Int?,
        hoursPlayed: Int,
        userNotes: String?,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val existing = libraryRepository.getLibraryEntryFlow(gameId).first()
            val entry = LibraryEntry(
                gameId = gameId,
                status = status,
                userRating = userRating,
                userNotes = userNotes?.trim()?.takeIf { it.isNotEmpty() },
                isFavorite = isFavorite,
                addedAtEpochSeconds = existing?.addedAtEpochSeconds ?: now,
                updatedAtEpochSeconds = now,
                hoursPlayed = hoursPlayed
            )
            when (val result = libraryRepository.saveLibraryEntry(entry)) {
                is AppResult.Success -> {
                    _flags.update { it.copy(isEditingLibrary = false, message = null) }
                }
                is AppResult.Error -> {
                    _flags.update {
                        it.copy(
                            isEditingLibrary = true,
                            message = result.error to R.string.error_library_update_failed
                        )
                    }
                }
            }
        }
    }

    fun onRemoveFromLibrary() {
        viewModelScope.launch {
            when (val result = libraryRepository.removeGameFromLibrary(gameId)) {
                is AppResult.Success -> {
                    _flags.update { it.copy(isEditingLibrary = false, message = null) }
                }
                is AppResult.Error -> {
                    _flags.update {
                        it.copy(
                            isEditingLibrary = true,
                            message = result.error to R.string.error_library_remove_failed
                        )
                    }
                }
            }
        }
    }

    private fun refreshDetails(force: Boolean, isUserPullRefresh: Boolean = false) {
        // Single-flight: a refresh in progress swallows retries, PTR and the
        // eviction guard alike; they are all idempotent over fresh data.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hasCachedData = gameRepository.getGameDetailsFlow(gameId).first() != null
            if (isUserPullRefresh) {
                _flags.update { it.copy(isRefreshing = true) }
            } else if (!hasCachedData) {
                _flags.update { it.copy(isLoading = true, message = null) }
            } else {
                _flags.update { it.copy(isLoading = false) }
            }

            try {
                when (val result = gameRepository.refreshGameDetails(gameId, force = force)) {
                    is AppResult.Success -> _flags.update { it.copy(message = null) }
                    is AppResult.Error -> _flags.update { it.copy(message = result.error to null) }
                }
            } finally {
                if (isUserPullRefresh) {
                    _flags.update { it.copy(isRefreshing = false) }
                } else {
                    _flags.update { it.copy(isLoading = false) }
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

    private data class DetailsInternalFlags(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isEditingLibrary: Boolean = false,
        val message: Pair<AppError?, Int?>? = null
    )

}
