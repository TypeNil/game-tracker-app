package io.github.typenil.gametracker.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.github.typenil.gametracker.feature.search.SearchRoute

/**
 * Extension for navigating to the Search screen.
 */
fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    navigate(route = SearchKey, navOptions = navOptions)
}

/**
 * Registers the Search destination in the type-safe [NavGraphBuilder].
 */
fun NavGraphBuilder.searchEntry(
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    composable<SearchKey> {
        SearchRoute(
            onGameClick = onGameClick,
            onBackClick = onBackClick
        )
    }
}
