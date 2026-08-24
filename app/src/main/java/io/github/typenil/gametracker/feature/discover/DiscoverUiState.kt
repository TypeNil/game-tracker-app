package io.github.typenil.gametracker.feature.discover

import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game

data class DiscoverUiState(
    val recommendations: List<DiscoverRecommendation> = emptyList(),
    val trending: List<Game> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val userMessageRes: Int? = null,
) {
    val isInitialLoading: Boolean
        get() = isLoading && recommendations.isEmpty() && trending.isEmpty()

    val showForYou: Boolean
        get() = recommendations.isNotEmpty()

    val hasContent: Boolean
        get() = recommendations.isNotEmpty() || trending.isNotEmpty()
}
