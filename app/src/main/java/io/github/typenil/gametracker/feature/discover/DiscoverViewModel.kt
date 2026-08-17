package io.github.typenil.gametracker.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val WHILE_SUBSCRIBED_TIMEOUT_MS = 5_000L

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState(isLoading = true))
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = DiscoverUiState(isLoading = true)
        )

    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    init {
        loadTopRatedGames()
    }

    fun retry() {
        loadTopRatedGames()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                when (val result = gameRepository.getTopRatedGames()) {
                    is AppResult.Success -> {
                        _uiState.update {
                            it.copy(
                                games = result.data,
                                error = null,
                                userMessageRes = null
                            )
                        }
                    }
                    is AppResult.Error -> {
                        _uiState.update { current ->
                            if (current.games.isNotEmpty()) {
                                current.copy(userMessageRes = R.string.error_refresh_failed)
                            } else {
                                current.copy(error = result.error)
                            }
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun onUserMessageShown() {
        _uiState.update { it.copy(userMessageRes = null) }
    }

    private fun loadTopRatedGames() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                when (val result = gameRepository.getTopRatedGames()) {
                    is AppResult.Success -> {
                        _uiState.update {
                            it.copy(
                                games = result.data,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.error
                            )
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
