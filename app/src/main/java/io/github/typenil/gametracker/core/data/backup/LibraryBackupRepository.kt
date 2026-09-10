package io.github.typenil.gametracker.core.data.backup

import io.github.typenil.gametracker.core.model.AppResult

interface LibraryBackupRepository {
    suspend fun exportLibrary(): AppResult<ByteArray>

    suspend fun previewImport(file: LibraryBackupFile): AppResult<LibraryImportPreview>

    suspend fun importLibrary(
        file: LibraryBackupFile,
        mode: LibraryImportMode,
    ): AppResult<Unit>
}
