package io.github.typenil.gametracker.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.core.app.OnNewIntentProvider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.feature.details.navigation.gameDetailsEntry
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.discover.navigation.discoverEntry
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey
import io.github.typenil.gametracker.feature.library.navigation.libraryEntry
import io.github.typenil.gametracker.feature.search.navigation.searchEntry
import io.github.typenil.gametracker.feature.settings.navigation.settingsEntry

/**
 * Root Navigation Host coordinating destinations, bottom navigation, deep links, and transitions.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    appState: GameTrackerAppState = rememberGameTrackerAppState()
) {
    val activity = LocalActivity.current ?: (LocalContext.current as? ComponentActivity)
    DisposableEffect(activity, appState.navController) {
        val provider = activity as? OnNewIntentProvider
        val listener = Consumer<Intent> { intent ->
            appState.navController.handleDeepLink(intent)
        }
        provider?.addOnNewIntentListener(listener)
        onDispose {
            provider?.removeOnNewIntentListener(listener)
        }
    }

    val isTopLevelDestination = appState.isTopLevelDestination
    val currentDestination = appState.currentDestination
    var scrollToTopDiscoverTrigger by remember { mutableStateOf(0L) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    val isDiscoverSelected = currentDestination?.hasRoute<DiscoverKey>() == true
                    NavigationBarItem(
                        selected = isDiscoverSelected,
                        onClick = {
                            if (isDiscoverSelected) {
                                scrollToTopDiscoverTrigger = System.currentTimeMillis()
                            } else {
                                appState.navigateToDiscover()
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

                    val isLibrarySelected = currentDestination?.hasRoute<LibraryKey>() == true
                    NavigationBarItem(
                        selected = isLibrarySelected,
                        onClick = {
                            if (!isLibrarySelected) {
                                appState.navigateToLibrary()
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
            navController = appState.navController,
            startDestination = DiscoverKey,
            modifier = Modifier.padding(innerPadding)
        ) {
            discoverEntry(
                onGameClick = appState::navigateToGameDetails,
                onSearchClick = appState::navigateToSearch,
                onAboutClick = appState::navigateToSettings,
                scrollToTopTrigger = { scrollToTopDiscoverTrigger },
            )
            libraryEntry(
                onGameClick = appState::navigateToGameDetails,
                onNavigateToDiscover = appState::navigateToDiscover
            )

            searchEntry(
                onGameClick = appState::navigateToGameDetails,
                onBackClick = appState::navigateBack
            )

            gameDetailsEntry(
                onGameClick = appState::navigateToGameDetails,
                onBackClick = appState::navigateBack
            )

            settingsEntry(
                onBackClick = appState::navigateBack
            )
        }
    }
}
