package io.github.typenil.gametracker.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.feature.details.navigation.gameDetailsEntry
import io.github.typenil.gametracker.feature.details.navigation.navigateToGameDetails
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.discover.navigation.discoverEntry
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey
import io.github.typenil.gametracker.feature.library.navigation.libraryEntry
import io.github.typenil.gametracker.feature.search.navigation.navigateToSearch
import io.github.typenil.gametracker.feature.search.navigation.searchEntry

/**
 * Root Navigation Host coordinating destinations, bottom navigation, and cross-feature transitions.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevelDestination = currentDestination?.hasRoute<DiscoverKey>() == true ||
        currentDestination?.hasRoute<LibraryKey>() == true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination?.hasRoute<DiscoverKey>() == true,
                        onClick = {
                            navController.navigate(DiscoverKey) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = stringResource(R.string.nav_discover)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_discover)) }
                    )

                    NavigationBarItem(
                        selected = currentDestination?.hasRoute<LibraryKey>() == true,
                        onClick = {
                            navController.navigate(LibraryKey) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CollectionsBookmark,
                                contentDescription = stringResource(R.string.nav_library)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_library)) }
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DiscoverKey,
            modifier = Modifier.padding(innerPadding)
        ) {
            discoverEntry(
                onGameClick = { gameId ->
                    navController.navigateToGameDetails(gameId = gameId)
                },
                onSearchClick = {
                    navController.navigateToSearch()
                }
            )

            libraryEntry(
                onGameClick = { gameId ->
                    navController.navigateToGameDetails(gameId = gameId)
                },
                onNavigateToDiscover = {
                    navController.navigate(DiscoverKey) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
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
}
