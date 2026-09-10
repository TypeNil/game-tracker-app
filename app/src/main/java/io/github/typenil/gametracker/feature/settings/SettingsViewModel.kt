package io.github.typenil.gametracker.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.backup.DocumentBytesStore
import io.github.typenil.gametracker.core.data.backup.LibraryBackupCodec
import io.github.typenil.gametracker.core.data.backup.LibraryBackupError
import io.github.typenil.gametracker.core.data.backup.LibraryBackupFile
import io.github.typenil.gametracker.core.data.backup.LibraryBackupParseResult
import io.github.typenil.gametracker.core.data.backup.LibraryBackupRepository
import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.data.backup.LibraryImportPreview
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.ThemeMode
import io.github.typenil.gametracker.core.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val libraryBackupRepository: LibraryBackupRepository,
    private val documentBytesStore: DocumentBytesStore,
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

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _importPreview = MutableStateFlow<LibraryImportPreview?>(null)
    val importPreview: StateFlow<LibraryImportPreview?> = _importPreview.asStateFlow()

    private var pendingImport: LibraryBackupFile? = null

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

    fun onExportDocumentPicked(uri: Uri) {
        viewModelScope.launch {
            if (_backupBusy.value) return@launch
            _backupBusy.value = true
            try {
                when (val exported = libraryBackupRepository.exportLibrary()) {
                    is AppResult.Success -> writeExport(uri, exported.data)
                    is AppResult.Error -> {
                        _userMessageRes.value = R.string.settings_backup_failed
                    }
                }
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun onImportDocumentPicked(uri: Uri) {
        viewModelScope.launch {
            if (_backupBusy.value) return@launch
            _backupBusy.value = true
            try {
                val bytes = try {
                    documentBytesStore.read(uri)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    _userMessageRes.value = ioErrorMessage(error)
                    return@launch
                }
                when (val parsed = LibraryBackupCodec.decode(bytes)) {
                    is LibraryBackupParseResult.Failure -> {
                        _userMessageRes.value = backupErrorMessage(parsed.error)
                    }
                    is LibraryBackupParseResult.Success -> {
                        when (val preview = libraryBackupRepository.previewImport(parsed.file)) {
                            is AppResult.Success -> {
                                pendingImport = parsed.file
                                _importPreview.value = preview.data
                            }
                            is AppResult.Error -> {
                                _userMessageRes.value = R.string.settings_backup_failed
                            }
                        }
                    }
                }
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun confirmImport(mode: LibraryImportMode) {
        val file = pendingImport ?: return
        viewModelScope.launch {
            if (_backupBusy.value) return@launch
            _backupBusy.value = true
            try {
                when (libraryBackupRepository.importLibrary(file, mode)) {
                    is AppResult.Success -> {
                        pendingImport = null
                        _importPreview.value = null
                        _userMessageRes.value = R.string.settings_import_success
                    }
                    is AppResult.Error -> {
                        _userMessageRes.value = R.string.settings_backup_failed
                    }
                }
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun dismissImportPreview() {
        pendingImport = null
        _importPreview.value = null
    }

    fun onUserMessageShown() {
        _userMessageRes.value = null
    }

    private suspend fun writeExport(uri: Uri, bytes: ByteArray) {
        try {
            documentBytesStore.write(uri, bytes)
            _userMessageRes.value = R.string.settings_export_success
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            _userMessageRes.value = ioErrorMessage(error)
        }
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

    private fun ioErrorMessage(error: IOException): Int {
        return if (error.message == LibraryBackupError.TOO_LARGE.name) {
            R.string.settings_backup_too_large
        } else {
            R.string.settings_backup_failed
        }
    }

    private fun backupErrorMessage(error: LibraryBackupError): Int = when (error) {
        LibraryBackupError.UNSUPPORTED_SCHEMA -> R.string.settings_backup_unsupported
        LibraryBackupError.INVALID_DOCUMENT -> R.string.settings_backup_invalid
        LibraryBackupError.TOO_LARGE -> R.string.settings_backup_too_large
        LibraryBackupError.IO -> R.string.settings_backup_failed
    }
}
