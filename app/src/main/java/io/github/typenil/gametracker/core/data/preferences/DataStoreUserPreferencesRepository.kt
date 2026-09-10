package io.github.typenil.gametracker.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.RecommendationTagCatalog
import io.github.typenil.gametracker.core.model.ThemeMode
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            val storedGenres = prefs[KEY_GENRES].orEmpty()
            val storedThemes = prefs[KEY_THEMES].orEmpty()
            val (genres, themes) = RecommendationTagCatalog.split(storedGenres + storedThemes)
            UserPreferences(
                recommendationGenres = genres,
                recommendationThemes = themes,
                recommendationPlatforms = prefs[KEY_PLATFORMS].orEmpty(),
                recommendationOnboardingDismissed = prefs[KEY_DISMISSED] ?: false,
                themeMode = ThemeMode.fromStorage(prefs[KEY_THEME_MODE]),
                dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            )
        }
        .flowOn(ioDispatcher)

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
        return write {
            it[KEY_GENRES] = genreTags
            it[KEY_THEMES] = themeTags
            it[KEY_PLATFORMS] = platforms
            it[KEY_DISMISSED] = true
        }
    }

    override suspend fun skipRecommendationOnboarding(): AppResult<Unit> {
        return write { it[KEY_DISMISSED] = true }
    }

    override suspend fun clearRecommendationPreferences(): AppResult<Unit> {
        return write {
            it[KEY_GENRES] = emptySet()
            it[KEY_THEMES] = emptySet()
            it[KEY_PLATFORMS] = emptySet()
            it[KEY_DISMISSED] = true
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit> {
        return write { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean): AppResult<Unit> {
        return write { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    private suspend fun write(block: suspend (MutablePreferences) -> Unit): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { block(it) }
                AppResult.Success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                AppResult.Error(AppError.UnknownError(error))
            }
        }

    private companion object {
        val KEY_GENRES = stringSetPreferencesKey("recommendation_genres")
        val KEY_THEMES = stringSetPreferencesKey("recommendation_themes")
        val KEY_PLATFORMS = stringSetPreferencesKey("recommendation_platforms")
        val KEY_DISMISSED = booleanPreferencesKey("recommendation_onboarding_dismissed")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
