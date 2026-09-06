package io.github.typenil.gametracker.feature.details.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.typenil.gametracker.feature.details.GameDetailsRoute

/**
 * Extension for navigating to the Game Details screen.
 * Uses launchSingleTop = true to prevent pushing duplicate instances of the
 * exact same gameId consecutively (e.g. rapid double-tap), while still allowing
 * normal backstack accumulation when drilling down into different similar games.
 */
fun NavController.navigateToGameDetails(gameId: Long, navOptions: NavOptions? = null) {
    if (navOptions != null) {
        navigate(route = GameDetailsKey(gameId = gameId), navOptions = navOptions)
    } else {
        navigate(route = GameDetailsKey(gameId = gameId)) {
            launchSingleTop = true
        }
    }
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
