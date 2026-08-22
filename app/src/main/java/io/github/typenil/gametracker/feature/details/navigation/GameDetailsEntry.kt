package io.github.typenil.gametracker.feature.details.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.typenil.gametracker.feature.details.GameDetailsRoute

/**
 * Extension for navigating to the Game Details screen.
 * No launchSingleTop: similar games push new details destinations with
 * different ids, and stacking is the intended behavior.
 */
fun NavController.navigateToGameDetails(gameId: Long, navOptions: NavOptions? = null) {
    navigate(route = GameDetailsKey(gameId = gameId), navOptions = navOptions)
}

/**
 * Registers the Game Details destination in the type-safe [NavGraphBuilder].
 * The gameId flows to the ViewModel through SavedStateHandle route arguments.
 */
fun NavGraphBuilder.gameDetailsEntry(
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    composable<GameDetailsKey>(
        deepLinks = listOf(
            navDeepLink<GameDetailsKey>(
                basePath = "gametracker://game"
            )
        )
    ) {
        GameDetailsRoute(
            onGameClick = onGameClick,
            onBackClick = onBackClick
        )
    }
}
