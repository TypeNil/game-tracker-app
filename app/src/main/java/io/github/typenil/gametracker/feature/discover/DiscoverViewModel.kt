package io.github.typenil.gametracker.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.model.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val WHILE_SUBSCRIBED_TIMEOUT_MS = 5_000L

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _rawState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<DiscoverUiState> = combine(_rawState, _isRefreshing) { state, isRefreshing ->
        if (state is DiscoverUiState.Success) {
            state.copy(isRefreshing = isRefreshing)
        } else {
            state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = DiscoverUiState.Loading
    )

    init {
        loadTopRatedGames()
    }

    fun retry() {
        loadTopRatedGames()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = gameRepository.getTopRatedGames()) {
                is AppResult.Success -> {
                    _rawState.value = DiscoverUiState.Success(games = result.data)
                }
                is AppResult.Error -> {
                    // On refresh failure with existing data, keep existing data or update error
                    if (_rawState.value !is DiscoverUiState.Success) {
                        _rawState.value = DiscoverUiState.Error(error = result.error)
                    }
                }
            }
            _isRefreshing.value = false
        }
    }

    private fun loadTopRatedGames() {
        viewModelScope.launch {
            _rawState.value = DiscoverUiState.Loading
            when (val result = gameRepository.getTopRatedGames()) {
                is AppResult.Success -> {
                    _rawState.value = DiscoverUiState.Success(games = result.data)
                }
                is AppResult.Error -> {
                    _rawState.value = DiscoverUiState.Error(error = result.error)
                }
            }
        }
    }
}
