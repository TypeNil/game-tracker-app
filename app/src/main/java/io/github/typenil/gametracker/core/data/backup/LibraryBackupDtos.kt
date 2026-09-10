package io.github.typenil.gametracker.core.data.backup

import kotlinx.serialization.Serializable

@Serializable
internal data class LibraryBackupFileDto(
    val schemaVersion: Int,
    val exportedAt: String,
    val library: List<LibraryBackupItemDto>,
)

@Serializable
internal data class LibraryBackupItemDto(
    val gameId: Long,
    val status: String,
    val rating: Int? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    val hours: Int = 0,
    val releaseNotificationsEnabled: Boolean = false,
    val addedAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val game: GameSnapshotDto,
)

@Serializable
internal data class GameSnapshotDto(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
)
