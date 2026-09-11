package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.data.backup.LibraryBackupItem
import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.model.LibraryStatus

internal data class DevSeedRequest(
    val preset: DevSeedPreset,
    val count: Int,
    val seed: Long,
    val mode: LibraryImportMode,
)

/** A generated seed, split by whether any remote source can resolve its games. */
internal data class DevSeed(
    val items: List<LibraryBackupItem>,
    val syntheticItems: List<LibraryBackupItem>,
)

internal data class DevSeedOutcome(
    val preset: DevSeedPreset,
    val requestedCount: Int,
    val deliveredCount: Int,
    val mode: LibraryImportMode,
    /** False when the generated games' catalog rows could not be written; the library is still seeded. */
    val catalogHydrated: Boolean,
)

/**
 * State observed *after* a wipe by reading each store back, so a wipe that did not take effect is
 * visible in the result instead of being reported as a success.
 */
internal data class DevWipeOutcome(
    val target: DevWipeTarget,
    val libraryEntries: Int,
    val searchHistoryEntries: Int,
    val notificationEvents: Int,
)

internal data class DevDiagnostics(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val flavor: String,
    val buildType: String,
    val roomSchemaVersion: Int,
    val databaseBytes: Long,
    val writeAheadLogBytes: Long,
    val libraryEntries: Int,
    val libraryEntriesByStatus: Map<LibraryStatus, Int>,
    val notificationEvents: Int,
    val searchHistoryEntries: Int,
    val bffOverride: String,
) {
    fun toClipboardText(): String = buildString {
        appendLine("GameTracker developer diagnostics")
        appendLine("app: $applicationId $versionName ($versionCode) $flavor/$buildType")
        appendLine("room: schema v$roomSchemaVersion, db ${databaseBytes}B, wal ${writeAheadLogBytes}B")
        appendLine("library: $libraryEntries")
        libraryEntriesByStatus.forEach { (status, count) -> appendLine("  ${status.name}: $count") }
        appendLine("notification ledger: $notificationEvents")
        appendLine("search history: $searchHistoryEntries")
        appendLine("bff override: ${bffOverride.ifEmpty { "(default)" }}")
    }
}
