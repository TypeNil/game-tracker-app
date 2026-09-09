package io.github.typenil.gametracker.feature.library.insights

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryGame

sealed interface LibraryInsightsUiState {
    data object Loading : LibraryInsightsUiState
    data class Error(val error: AppError) : LibraryInsightsUiState
    data object Empty : LibraryInsightsUiState
    data class Content(val insights: LibraryInsights) : LibraryInsightsUiState
}

internal fun toLibraryInsightsUiState(
    result: AppResult<List<LibraryGame>>,
): LibraryInsightsUiState = when (result) {
    is AppResult.Error -> LibraryInsightsUiState.Error(result.error)
    is AppResult.Success -> {
        val insights = computeLibraryInsights(result.data)
        if (insights.totalGames == 0) {
            LibraryInsightsUiState.Empty
        } else {
            LibraryInsightsUiState.Content(insights)
        }
    }
}
