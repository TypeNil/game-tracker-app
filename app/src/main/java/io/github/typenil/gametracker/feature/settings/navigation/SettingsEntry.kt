package io.github.typenil.gametracker.feature.settings.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.typenil.gametracker.feature.settings.SettingsRoute
import io.github.typenil.gametracker.feature.settings.SettingsViewModel

/**
 * Registers the About/Settings destination in the type-safe [NavGraphBuilder].
 */
fun NavGraphBuilder.settingsEntry(
    onBackClick: () -> Unit
) {
    composable<SettingsKey> {
        val viewModel: SettingsViewModel = hiltViewModel()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val preferencesLoaded by viewModel.preferencesLoaded.collectAsStateWithLifecycle()
        val userMessageRes by viewModel.userMessageRes.collectAsStateWithLifecycle()
        SettingsRoute(
            onBackClick = onBackClick,
            recommendationGenres = preferences.recommendationGenres,
            recommendationThemes = preferences.recommendationThemes,
            recommendationPlatforms = preferences.recommendationPlatforms,
            onboardingDismissed = preferences.recommendationOnboardingDismissed,
            recommendationPreferencesLoaded = preferencesLoaded,
            onSaveRecommendationPreferences = viewModel::saveRecommendationPreferences,
            onSkipRecommendationOnboarding = viewModel::skipRecommendationOnboarding,
            onResetRecommendationPreferences = viewModel::resetRecommendationPreferences,
            userMessageRes = userMessageRes,
            onUserMessageShown = viewModel::onUserMessageShown,
            themeMode = preferences.themeMode,
            dynamicColor = preferences.dynamicColor,
            onThemeModeChange = viewModel::onThemeModeSelected,
            onDynamicColorChange = viewModel::onDynamicColorChanged,
        )
    }
}
