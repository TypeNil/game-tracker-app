package io.github.typenil.gametracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.ThemeMode
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _preferencesLoaded = MutableStateFlow(false)
    val preferencesLoaded: StateFlow<Boolean> = _preferencesLoaded.asStateFlow()

    val preferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences
        .onEach { _preferencesLoaded.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences(),
        )

    private val _userMessageRes = MutableStateFlow<Int?>(null)
    val userMessageRes: StateFlow<Int?> = _userMessageRes.asStateFlow()

    suspend fun saveRecommendationPreferences(genres: Set<String>, platforms: Set<String>): Boolean {
        return recordPreferenceWrite(userPreferencesRepository.setRecommendationPreferences(genres, platforms))
    }

    suspend fun skipRecommendationOnboarding(): Boolean {
        return recordPreferenceWrite(userPreferencesRepository.skipRecommendationOnboarding())
    }

    suspend fun resetRecommendationPreferences(): Boolean {
        return recordPreferenceWrite(userPreferencesRepository.clearRecommendationPreferences())
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch {
            recordPreferenceWrite(userPreferencesRepository.setThemeMode(mode))
        }
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch {
            recordPreferenceWrite(userPreferencesRepository.setDynamicColor(enabled))
        }
    }

    fun onUserMessageShown() {
        _userMessageRes.value = null
    }

    private fun recordPreferenceWrite(result: AppResult<Unit>): Boolean {
        return when (result) {
            is AppResult.Success -> {
                clearPreferenceError()
                true
            }
            is AppResult.Error -> {
                _userMessageRes.value = R.string.error_preferences_save_failed
                false
            }
        }
    }

    private fun clearPreferenceError() {
        if (_userMessageRes.value == R.string.error_preferences_save_failed) {
            _userMessageRes.value = null
        }
    }
}
