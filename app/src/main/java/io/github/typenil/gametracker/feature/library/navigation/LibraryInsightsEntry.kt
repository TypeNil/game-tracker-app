package io.github.typenil.gametracker.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.typenil.gametracker.feature.library.insights.LibraryInsightsRoute

fun NavGraphBuilder.libraryInsightsEntry(
    onBackClick: () -> Unit,
    onGameClick: (Long) -> Unit,
) {
    composable<LibraryInsightsKey> {
        LibraryInsightsRoute(
            onBackClick = onBackClick,
            onGameClick = onGameClick,
        )
    }
}
