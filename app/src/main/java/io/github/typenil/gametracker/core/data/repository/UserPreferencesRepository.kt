package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setRecommendationPreferences(
        genres: Set<String>,
        platforms: Set<String>,
    ): AppResult<Unit>

    suspend fun skipRecommendationOnboarding(): AppResult<Unit>

    suspend fun clearRecommendationPreferences(): AppResult<Unit>
}
