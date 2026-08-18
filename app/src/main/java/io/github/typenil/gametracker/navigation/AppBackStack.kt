package io.github.typenil.gametracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * State holder managing top-level backstack and navigation state across the application.
 */
@Stable
class AppNavigationState(
    val navController: NavHostController
) {
    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    fun navigateUp() {
        navController.navigateUp()
    }
}

@Composable
fun rememberAppNavigationState(
    navController: NavHostController = rememberNavController()
): AppNavigationState {
    return remember(navController) {
        AppNavigationState(navController = navController)
    }
}
