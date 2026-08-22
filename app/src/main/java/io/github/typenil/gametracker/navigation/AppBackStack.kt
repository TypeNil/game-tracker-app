package io.github.typenil.gametracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.typenil.gametracker.feature.details.navigation.navigateToGameDetails
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey
import io.github.typenil.gametracker.feature.search.navigation.navigateToSearch

/**
 * State holder managing top-level navigation, tab switching, and back stack state (ADR-006).
 */
@Stable
class GameTrackerAppState(
    val navController: NavHostController
) {
    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val isTopLevelDestination: Boolean
        @Composable get() {
            val destination = currentDestination
            return destination?.hasRoute<DiscoverKey>() == true ||
                destination?.hasRoute<LibraryKey>() == true
        }

    fun navigateToDiscover() {
        navController.navigate(DiscoverKey) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToLibrary() {
        navController.navigate(LibraryKey) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToSearch() {
        navController.navigateToSearch()
    }

    fun navigateToGameDetails(gameId: Long) {
        navController.navigateToGameDetails(gameId = gameId)
    }

    fun navigateBack() {
        navController.popBackStack()
    }
}

@Composable
fun rememberGameTrackerAppState(
    navController: NavHostController = rememberNavController()
): GameTrackerAppState {
    return remember(navController) {
        GameTrackerAppState(navController = navController)
    }
}
