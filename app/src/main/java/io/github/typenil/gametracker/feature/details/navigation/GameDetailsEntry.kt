package io.github.typenil.gametracker.feature.details.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.typenil.gametracker.R

/**
 * Extension for navigating to the Game Details screen.
 */
fun NavController.navigateToGameDetails(gameId: Long, navOptions: NavOptions? = null) {
    navigate(route = GameDetailsKey(gameId = gameId), navOptions = navOptions)
}

/**
 * Registers the Game Details destination in the type-safe [NavGraphBuilder].
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.gameDetailsEntry(
    onBackClick: () -> Unit
) {
    composable<GameDetailsKey> { backStackEntry ->
        val key = backStackEntry.toRoute<GameDetailsKey>()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.details_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_action_desc)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.game_details_id_format, key.gameId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
