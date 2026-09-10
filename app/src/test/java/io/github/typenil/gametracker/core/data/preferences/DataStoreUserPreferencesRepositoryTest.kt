package io.github.typenil.gametracker.core.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import io.github.typenil.gametracker.core.data.InMemoryPreferencesDataStore
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreUserPreferencesRepositoryTest {

    @Test
    fun preferences_defaultToSystemThemeAndDynamicColor() = runTest {
        val repository = repository()

        repository.preferences.test {
            val prefs = awaitItem()
            assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
            assertTrue(prefs.dynamicColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setThemeMode_andSetDynamicColor_roundTrip() = runTest {
        val repository = repository()

        assertTrue(repository.setThemeMode(ThemeMode.DARK) is AppResult.Success)
        assertTrue(repository.setDynamicColor(false) is AppResult.Success)

        repository.preferences.test {
            val prefs = awaitItem()
            assertEquals(ThemeMode.DARK, prefs.themeMode)
            assertFalse(prefs.dynamicColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun preferences_unknownThemeModeFallsBackToSystem() = runTest {
        val store = InMemoryPreferencesDataStore()
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("theme_mode")] = "NEON"
                this[booleanPreferencesKey("dynamic_color")] = false
            }
        }
        val repository = repository(store)

        repository.preferences.test {
            val prefs = awaitItem()
            assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
            assertFalse(prefs.dynamicColor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recommendationWrites_doNotClearAppearance() = runTest {
        val repository = repository()
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setDynamicColor(false)

        repository.skipRecommendationOnboarding()

        repository.preferences.test {
            val prefs = awaitItem()
            assertEquals(ThemeMode.LIGHT, prefs.themeMode)
            assertFalse(prefs.dynamicColor)
            assertTrue(prefs.recommendationOnboardingDismissed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun repository(
        store: InMemoryPreferencesDataStore = InMemoryPreferencesDataStore(),
    ) = DataStoreUserPreferencesRepository(
        dataStore = store,
        ioDispatcher = UnconfinedTestDispatcher(),
    )
}
