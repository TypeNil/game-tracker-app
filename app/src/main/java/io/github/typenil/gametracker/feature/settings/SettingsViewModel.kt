package io.github.typenil.gametracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = userPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    private val _userMessageRes = MutableStateFlow<Int?>(null)
    val userMessageRes: StateFlow<Int?> = _userMessageRes.asStateFlow()

    suspend fun saveRecommendationPreferences(genres: Set<String>, platforms: Set<String>): Boolean {
        return when (userPreferencesRepository.setRecommendationPreferences(genres, platforms)) {
            is AppResult.Success -> true
            is AppResult.Error -> {
                _userMessageRes.value = R.string.error_preferences_save_failed
                false
            }
        }
    }

    suspend fun skipRecommendationOnboarding(): Boolean {
        return when (userPreferencesRepository.skipRecommendationOnboarding()) {
            is AppResult.Success -> true
            is AppResult.Error -> {
                _userMessageRes.value = R.string.error_preferences_save_failed
                false
            }
        }
    }

    fun onUserMessageShown() {
        _userMessageRes.value = null
    }
}
