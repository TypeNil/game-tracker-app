package io.github.typenil.gametracker.feature.library.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.github.typenil.gametracker.feature.library.LibraryRoute

/**
 * Extension for navigating to the Library screen.
 */
fun NavController.navigateToLibrary(navOptions: NavOptions? = null) {
    navigate(route = LibraryKey, navOptions = navOptions)
}

/**
 * Registers the Library destination in the type-safe [NavGraphBuilder].
 */
fun NavGraphBuilder.libraryEntry(
    onGameClick: (Long) -> Unit,
    onNavigateToDiscover: () -> Unit
) {
    composable<LibraryKey> {
        LibraryRoute(
            onGameClick = onGameClick,
            onNavigateToDiscover = onNavigateToDiscover
        )
    }
}
