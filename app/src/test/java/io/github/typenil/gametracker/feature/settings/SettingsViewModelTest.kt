package io.github.typenil.gametracker.feature.settings

import android.net.Uri
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.data.FakeUserPreferencesRepository
import io.github.typenil.gametracker.core.data.backup.DocumentBytesStore
import io.github.typenil.gametracker.core.data.backup.LibraryBackupError
import io.github.typenil.gametracker.core.data.backup.LibraryBackupFile
import io.github.typenil.gametracker.core.data.backup.LibraryBackupRepository
import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.data.backup.LibraryImportPreview
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val documentBytesStore: DocumentBytesStore = mockk(relaxed = true)
    private val uri: Uri = mockk()

    @Test
    fun export_tooLarge_showsTooLargeAndDoesNotWrite() = runTest {
        val viewModel = SettingsViewModel(
            userPreferencesRepository = FakeUserPreferencesRepository(),
            libraryBackupRepository = FakeLibraryBackupRepository(
                exportResult = AppResult.Error(
                    AppError.UnknownError(IOException(LibraryBackupError.TOO_LARGE.name)),
                ),
            ),
            documentBytesStore = documentBytesStore,
        )

        viewModel.onExportDocumentPicked(uri)

        assertEquals(R.string.settings_backup_too_large, viewModel.userMessageRes.value)
        coVerify(exactly = 0) { documentBytesStore.write(any(), any()) }
    }

    @Test
    fun export_unknownError_showsGenericFailureAndDoesNotWrite() = runTest {
        val viewModel = SettingsViewModel(
            userPreferencesRepository = FakeUserPreferencesRepository(),
            libraryBackupRepository = FakeLibraryBackupRepository(
                exportResult = AppResult.Error(AppError.UnknownError(IllegalStateException("boom"))),
            ),
            documentBytesStore = documentBytesStore,
        )

        viewModel.onExportDocumentPicked(uri)

        assertEquals(R.string.settings_backup_failed, viewModel.userMessageRes.value)
        coVerify(exactly = 0) { documentBytesStore.write(any(), any()) }
    }

    private class FakeLibraryBackupRepository(
        private val exportResult: AppResult<ByteArray>,
    ) : LibraryBackupRepository {
        override suspend fun exportLibrary(): AppResult<ByteArray> = exportResult

        override suspend fun previewImport(file: LibraryBackupFile): AppResult<LibraryImportPreview> {
            error("unused")
        }

        override suspend fun importLibrary(
            file: LibraryBackupFile,
            mode: LibraryImportMode,
        ): AppResult<Unit> {
            error("unused")
        }
    }
}
