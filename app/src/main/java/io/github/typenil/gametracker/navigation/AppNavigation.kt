package io.github.typenil.gametracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.typenil.gametracker.feature.details.navigation.gameDetailsEntry
import io.github.typenil.gametracker.feature.details.navigation.navigateToGameDetails
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.discover.navigation.discoverEntry
import io.github.typenil.gametracker.feature.search.navigation.navigateToSearch
import io.github.typenil.gametracker.feature.search.navigation.searchEntry

/**
 * Root Navigation Host coordinating destinations and cross-feature transitions.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = DiscoverKey,
        modifier = modifier
    ) {
        discoverEntry(
            onGameClick = { gameId ->
                navController.navigateToGameDetails(gameId = gameId)
            },
            onSearchClick = {
                navController.navigateToSearch()
            }
        )

        searchEntry(
            onGameClick = { gameId ->
                navController.navigateToGameDetails(gameId = gameId)
            },
            onBackClick = {
                navController.popBackStack()
            }
        )

        gameDetailsEntry(
            onGameClick = { gameId ->
                navController.navigateToGameDetails(gameId = gameId)
            },
            onBackClick = {
                navController.popBackStack()
            }
        )
    }
}
