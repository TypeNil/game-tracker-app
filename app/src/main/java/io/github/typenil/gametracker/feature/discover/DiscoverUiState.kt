package io.github.typenil.gametracker.feature.discover

import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.recommendations.DiscoverRecommendation
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.Game

enum class DiscoverTab(val titleRes: Int) {
    FOR_YOU(R.string.discover_tab_for_you),
    CHARTS(R.string.discover_tab_charts),
}

enum class DiscoverRail(val type: String, val titleRes: Int) {
    POPULAR_NOW("visits", R.string.discover_popular_now),
    PLAYING_NOW("playing", R.string.discover_playing_now),
    WANTED_NOW("wanted", R.string.discover_wanted_now),
    UPCOMING("upcoming", R.string.discover_upcoming),
    WATCHED_NOW("twitch", R.string.discover_watched_now),
}

data class DiscoverRailState(
    val rail: DiscoverRail,
    val games: List<Game> = emptyList(),
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
)

data class DiscoverUiState(
    val selectedTab: DiscoverTab = DiscoverTab.FOR_YOU,
    val selectedRail: DiscoverRail = DiscoverRail.POPULAR_NOW,
    val recommendations: List<DiscoverRecommendation> = emptyList(),
    val isColdStart: Boolean = false,
    val forYouLoading: Boolean = false,
    val forYouEndReached: Boolean = false,
    val trending: List<Game> = emptyList(),
    val rails: List<DiscoverRailState> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
    val userMessageRes: Int? = null,
) {
    val isInitialLoading: Boolean
        get() = isLoading && recommendations.isEmpty() && trending.isEmpty() && rails.all { it.games.isEmpty() }

    val showForYou: Boolean
        get() = recommendations.isNotEmpty()

    val hasContent: Boolean
        get() = recommendations.isNotEmpty() || trending.isNotEmpty() || rails.any { it.games.isNotEmpty() }
}
