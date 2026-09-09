package io.github.typenil.gametracker.core.data

import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {
    private val state = MutableStateFlow(initial)
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
        state.value = state.value.copy(
            recommendationGenres = genres,
            recommendationPlatforms = platforms,
            recommendationOnboardingDismissed = true,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun skipRecommendationOnboarding(): AppResult<Unit> {
        state.value = state.value.copy(recommendationOnboardingDismissed = true)
        return AppResult.Success(Unit)
    }
}
