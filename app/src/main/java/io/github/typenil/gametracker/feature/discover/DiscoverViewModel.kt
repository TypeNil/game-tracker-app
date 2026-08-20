package io.github.typenil.gametracker.feature.discover

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    private val _refreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<AppError?>(null)
    private val _userMessageRes = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<DiscoverUiState> = combine(
        gameRepository.getTopRatedGamesFlow(),
        _loading,
        _refreshing,
        _error,
        _userMessageRes
    ) { games, isLoading, isRefreshing, error, userMessageRes ->
        DiscoverUiState(
            games = games,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = if (games.isNotEmpty()) null else error,
            userMessageRes = if (games.isNotEmpty() && error != null) {
                userMessageRes ?: R.string.error_refresh_failed
            } else {
                userMessageRes
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscoverUiState(isLoading = true)
    )

    private var refreshJob: Job? = null

    init {
        loadTopRatedGames()
    }

    fun retry() {
        loadTopRatedGames()
    }

    fun refresh() {
        if (_refreshing.value) return
        performRefresh(isUserPullToRefresh = true)
    }

    fun onUserMessageShown() {
        _error.update { null }
        _userMessageRes.update { null }
    }

    private fun loadTopRatedGames() {
        performRefresh(isUserPullToRefresh = false)
    }

    private fun performRefresh(isUserPullToRefresh: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (isUserPullToRefresh) {
                _refreshing.value = true
            } else {
                _loading.value = true
                _error.value = null
            }

            try {
                when (val result = gameRepository.refreshTopRatedGames()) {
                    is AppResult.Success -> {
                        _error.value = null
                        _userMessageRes.value = null
                    }
                    is AppResult.Error -> {
                        _error.value = result.error
                    }
                }
            } finally {
                if (isUserPullToRefresh) {
                    _refreshing.value = false
                } else {
                    _loading.value = false
                }
            }
        }
    }
}


