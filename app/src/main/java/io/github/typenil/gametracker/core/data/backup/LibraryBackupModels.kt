package io.github.typenil.gametracker.core.data.backup

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import java.time.Instant

data class LibraryBackupFile(
    val schemaVersion: Int,
    val exportedAt: Instant,
    val items: List<LibraryBackupItem>,
)

data class LibraryBackupItem(
    val entry: LibraryEntry,
    val game: Game,
)

data class LibraryImportPreview(
    val foundCount: Int,
    val newCount: Int,
    val conflictCount: Int,
)

enum class LibraryImportMode {
    MERGE,
    REPLACE,
}

enum class LibraryBackupError {
    UNSUPPORTED_SCHEMA,
    INVALID_DOCUMENT,
    TOO_LARGE,
    IO,
}

sealed interface LibraryBackupParseResult {
    data class Success(val file: LibraryBackupFile) : LibraryBackupParseResult
    data class Failure(val error: LibraryBackupError) : LibraryBackupParseResult
}
