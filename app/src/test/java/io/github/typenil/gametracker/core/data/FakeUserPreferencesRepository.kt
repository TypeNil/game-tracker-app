package io.github.typenil.gametracker.core.data

import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.RecommendationTagCatalog
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {
    private val state = MutableStateFlow(migrate(initial))
    override val preferences: StateFlow<UserPreferences> = state.asStateFlow()

    override suspend fun setRecommendationPreferences(
        genres: Set<String>,
        platforms: Set<String>,
    ): AppResult<Unit> {
        if (!UserPreferences.isValidColdStart(genres, platforms)) {
            return AppResult.Error(
                AppError.UnknownError(IllegalArgumentException("invalid recommendation preferences")),
            )
        }
        val (genreTags, themeTags) = RecommendationTagCatalog.split(genres)
        state.value = state.value.copy(
            recommendationGenres = genreTags,
            recommendationThemes = themeTags,
            recommendationPlatforms = platforms,
            recommendationOnboardingDismissed = true,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun skipRecommendationOnboarding(): AppResult<Unit> {
        state.value = state.value.copy(recommendationOnboardingDismissed = true)
        return AppResult.Success(Unit)
    }

    override suspend fun clearRecommendationPreferences(): AppResult<Unit> {
        state.value = state.value.copy(
            recommendationGenres = emptySet(),
            recommendationThemes = emptySet(),
            recommendationPlatforms = emptySet(),
            recommendationOnboardingDismissed = true,
        )
        return AppResult.Success(Unit)
    }

    private companion object {
        fun migrate(initial: UserPreferences): UserPreferences {
            val (genres, themes) = RecommendationTagCatalog.split(
                initial.recommendationGenres + initial.recommendationThemes,
            )
            return initial.copy(
                recommendationGenres = genres,
                recommendationThemes = themes,
            )
        }
    }
}
