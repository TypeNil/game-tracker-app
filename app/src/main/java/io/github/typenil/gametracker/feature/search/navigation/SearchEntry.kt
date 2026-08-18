package io.github.typenil.gametracker.feature.search.navigation

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
import io.github.typenil.gametracker.R

/**
 * Extension for navigating to the Search screen.
 */
fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    navigate(route = SearchKey, navOptions = navOptions)
}

/**
 * Registers the Search destination in the type-safe [NavGraphBuilder].
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.searchEntry(
    onGameClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    composable<SearchKey> {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.search_title)) },
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
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
