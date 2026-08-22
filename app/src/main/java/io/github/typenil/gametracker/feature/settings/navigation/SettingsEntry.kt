package io.github.typenil.gametracker.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.typenil.gametracker.feature.settings.SettingsRoute

/**
 * Registers the About/Settings destination in the type-safe [NavGraphBuilder].
 */
fun NavGraphBuilder.settingsEntry(
    onBackClick: () -> Unit
) {
    composable<SettingsKey> {
        SettingsRoute(onBackClick = onBackClick)
    }
}
