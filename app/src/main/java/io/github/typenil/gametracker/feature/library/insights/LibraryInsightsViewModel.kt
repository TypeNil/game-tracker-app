package io.github.typenil.gametracker.feature.library.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class LibraryInsightsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val retryGeneration = MutableStateFlow(0)

    val uiState: StateFlow<LibraryInsightsUiState> = retryGeneration
        .flatMapLatest { libraryRepository.getLibraryGamesFlow() }
        .map(::toLibraryInsightsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = LibraryInsightsUiState.Loading,
        )

    fun onRetry() {
        retryGeneration.update { it + 1 }
    }
}
