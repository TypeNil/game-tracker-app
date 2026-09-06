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
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.feature.details.navigation.GameDetailsKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.connectivity.NetworkStatus
import io.github.typenil.gametracker.core.connectivity.reconnects
import javax.inject.Inject
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GameDetailsViewModel internal constructor(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    val gameId: Long,
    private val networkMonitor: NetworkMonitor? = null,
) : ViewModel() {

    @Inject
    constructor(
        gameRepository: GameRepository,
        libraryRepository: LibraryRepository,
        savedStateHandle: SavedStateHandle,
        networkMonitor: NetworkMonitor,
    ) : this(
        gameRepository,
        libraryRepository,
        savedStateHandle.toRoute<GameDetailsKey>().gameId,
        networkMonitor,
    )

    private val screenStarted = MutableStateFlow(false)
    private var checkRecoveryOnNextStart = false
    private val _flags = MutableStateFlow(DetailsInternalFlags())
    private val initialPreview: GameDetails? = gameRepository.getInitialGameDetails(gameId)

    fun onScreenStarted() {
        screenStarted.value = true

        val shouldCheck = checkRecoveryOnNextStart
        checkRecoveryOnNextStart = false

        if (
            shouldCheck &&
            networkMonitor?.status?.value == NetworkStatus.Available
        ) {
            viewModelScope.launch {
                recoverAfterConnectivityChange(
                    reloadImagesWhenHydrated = false,
                )
            }
        }
    }

    fun onScreenStopped() {
        checkRecoveryOnNextStart = true
        screenStarted.value = false
    }

    /**
     * WhileSubscribed(5_000): stops collecting Room and library pipelines when covered
     * in the back stack, avoiding redundant work while another destination is active.
     */
    val uiState: StateFlow<GameDetailsUiState> = combine(
        gameRepository.getGameDetailsFlow(gameId),
        gameRepository.isGameDetailsHydratedFlow(gameId),
        libraryRepository.getLibraryEntryFlow(gameId),
        _flags
    ) { game, isHydrated, libraryResult, flags ->
        val libraryEntry = when (libraryResult) {
            is AppResult.Success -> libraryResult.data
            is AppResult.Error -> null
        }
        val libraryLoadError = when (libraryResult) {
            is AppResult.Error -> libraryResult.error
            is AppResult.Success -> null
        }
        val error = flags.message?.first
        val displayedGame = game ?: if (flags.isLoading) initialPreview else null
        GameDetailsUiState(
            game = displayedGame,
            libraryEntry = libraryEntry,
            isHydrated = isHydrated && game != null,
            isLoading = flags.isLoading,
            isRefreshing = flags.isRefreshing,
            isEditingLibrary = flags.isEditingLibrary,
            isLibrarySubmitting = flags.isSubmitting,
            error = if (displayedGame != null) null else error,
            libraryLoadError = libraryLoadError,
            userMessageRes = if (displayedGame != null && error != null) {
                flags.message?.second ?: R.string.error_refresh_failed
            } else {
                flags.message?.second
            },
            imageReloadToken = flags.imageReloadToken,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GameDetailsUiState(
            game = initialPreview,
            isLoading = initialPreview == null
        )
    )

    private var refreshJob: Job? = null
    private var libraryMutationJob: Job? = null

    init {
        refreshDetails(force = false)
        observeEviction()
        observeNetworkReconnect()
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
        if (uiState.value.libraryLoadError != null) return
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
        val loadError = uiState.value.libraryLoadError
        if (loadError != null) {
            _flags.update {
                it.copy(
                    isEditingLibrary = false,
                    message = loadError to R.string.error_library_load_failed,
                )
            }
            return
        }

        mutateLibrary {
            val now = System.currentTimeMillis() / 1000
            val existing = when (val observed = libraryRepository.getLibraryEntryFlow(gameId).first()) {
                is AppResult.Success -> observed.data
                is AppResult.Error -> {
                    _flags.update {
                        it.copy(
                            isEditingLibrary = true,
                            message = observed.error to R.string.error_library_update_failed,
                        )
                    }
                    return@mutateLibrary
                }
            }
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
        mutateLibrary {
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

    private fun mutateLibrary(block: suspend () -> Unit) {
        if (libraryMutationJob?.isActive == true) return
        libraryMutationJob = viewModelScope.launch {
            _flags.update { it.copy(isSubmitting = true) }
            try {
                block()
            } finally {
                _flags.update { it.copy(isSubmitting = false) }
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
                    is AppResult.Success -> _flags.update {
                        it.copy(
                            message = null,
                            lastDetailsRefreshFailed = false,
                        )
                    }
                    is AppResult.Error -> _flags.update {
                        it.copy(
                            message = result.error to null,
                            lastDetailsRefreshFailed = true,
                        )
                    }
                }
            } finally {
                _flags.update { current ->
                    current.copy(
                        isRefreshing = false,
                        isLoading = if (isUserPullRefresh) current.isLoading else false,
                        imageReloadToken = if (force) {
                            current.imageReloadToken + 1
                        } else {
                            current.imageReloadToken
                        },
                    )
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
            screenStarted
                .flatMapLatest { started ->
                    if (started) gameRepository.isGameDetailsHydratedFlow(gameId) else emptyFlow()
                }
                .collect { hydrated ->
                    if (wasHydrated && !hydrated) {
                        refreshDetails(force = true)
                    }
                    wasHydrated = hydrated
                }
        }
    }

    /**
     * Reconnect guard: when device connectivity transitions Unavailable -> Available,
     * automatically refetch full details if the current state is unhydrated (skeleton)
     * or the previous refresh completed with an error.
     */
    private fun incrementImageReloadToken() {
        _flags.update {
            it.copy(imageReloadToken = it.imageReloadToken + 1)
        }
    }

    private suspend fun recoverAfterConnectivityChange(
        reloadImagesWhenHydrated: Boolean,
    ) {
        refreshJob?.join()

        val shouldRecover =
            !gameRepository.isGameDetailsHydratedFlow(gameId).first() ||
                _flags.value.lastDetailsRefreshFailed

        if (shouldRecover) {
            refreshDetails(force = true)
        } else if (reloadImagesWhenHydrated) {
            incrementImageReloadToken()
        }
    }

    private fun observeNetworkReconnect() {
        val monitor = networkMonitor ?: return
        viewModelScope.launch {
            screenStarted
                .flatMapLatest { started ->
                    if (started) monitor.status.reconnects() else emptyFlow()
                }
                .collect {
                    recoverAfterConnectivityChange(
                        reloadImagesWhenHydrated = true,
                    )
                }
        }
    }

    private data class DetailsInternalFlags(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isEditingLibrary: Boolean = false,
        val isSubmitting: Boolean = false,
        val message: Pair<AppError?, Int?>? = null,
        val lastDetailsRefreshFailed: Boolean = false,
        val imageReloadToken: Long = 0L,
    )


}
