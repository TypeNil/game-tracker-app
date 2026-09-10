package io.github.typenil.gametracker.core.data.backup

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeParseException

object LibraryBackupCodec {
    const val SCHEMA_VERSION = 1

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = false
        coerceInputValues = false
    }

    fun encode(file: LibraryBackupFile): ByteArray {
        val dto = LibraryBackupFileDto(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = file.exportedAt.toString(),
            library = file.items.map { it.toDto() },
        )
        return json.encodeToString(dto).encodeToByteArray()
    }

    fun decode(bytes: ByteArray): LibraryBackupParseResult {
        val dto = try {
            json.decodeFromString<LibraryBackupFileDto>(bytes.decodeToString())
        } catch (_: SerializationException) {
            return LibraryBackupParseResult.Failure(LibraryBackupError.INVALID_DOCUMENT)
        } catch (_: IllegalArgumentException) {
            return LibraryBackupParseResult.Failure(LibraryBackupError.INVALID_DOCUMENT)
        }
        if (dto.schemaVersion != SCHEMA_VERSION) {
            return LibraryBackupParseResult.Failure(LibraryBackupError.UNSUPPORTED_SCHEMA)
        }
        return try {
            LibraryBackupParseResult.Success(dto.toDomain())
        } catch (_: IllegalArgumentException) {
            LibraryBackupParseResult.Failure(LibraryBackupError.INVALID_DOCUMENT)
        } catch (_: DateTimeParseException) {
            LibraryBackupParseResult.Failure(LibraryBackupError.INVALID_DOCUMENT)
        }
    }

    private fun LibraryBackupItem.toDto(): LibraryBackupItemDto = LibraryBackupItemDto(
        gameId = entry.gameId,
        status = LibraryBackupV1Status.toWire(entry.status),
        rating = entry.userRating,
        notes = entry.userNotes,
        favorite = entry.isFavorite,
        hours = entry.hoursPlayed,
        releaseNotificationsEnabled = entry.releaseNotificationsEnabled,
        addedAtEpochSeconds = entry.addedAtEpochSeconds,
        updatedAtEpochSeconds = entry.updatedAtEpochSeconds,
        game = GameSnapshotDto(
            id = game.id,
            name = game.name,
            coverUrl = game.coverUrl,
            rating = game.rating,
            releaseDateEpochSeconds = game.releaseDateEpochSeconds,
            summary = game.summary,
            genres = game.genres,
            platforms = game.platforms,
        ),
    )

    private fun LibraryBackupFileDto.toDomain(): LibraryBackupFile {
        val exportedAt = Instant.parse(exportedAt)
        val seenIds = HashSet<Long>(library.size)
        val items = library.map { item ->
            if (!seenIds.add(item.gameId)) {
                throw IllegalArgumentException("duplicate gameId ${item.gameId}")
            }
            item.toDomain()
        }
        return LibraryBackupFile(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = exportedAt,
            items = items,
        )
    }

    private fun LibraryBackupItemDto.toDomain(): LibraryBackupItem {
        if (gameId <= 0L || game.id != gameId) {
            throw IllegalArgumentException("game id mismatch")
        }
        if (game.name.isBlank()) {
            throw IllegalArgumentException("blank game name")
        }
        val status = LibraryBackupV1Status.toDomain(status)
            ?: throw IllegalArgumentException("unknown status $status")
        if (hours < 0) {
            throw IllegalArgumentException("negative hours")
        }
        if (rating != null && rating !in 1..10) {
            throw IllegalArgumentException("rating out of range")
        }
        if (addedAtEpochSeconds < 0L || updatedAtEpochSeconds < addedAtEpochSeconds) {
            throw IllegalArgumentException("invalid timestamps")
        }
        return LibraryBackupItem(
            entry = LibraryEntry(
                gameId = gameId,
                status = status,
                userRating = rating,
                userNotes = notes,
                isFavorite = favorite,
                addedAtEpochSeconds = addedAtEpochSeconds,
                updatedAtEpochSeconds = updatedAtEpochSeconds,
                hoursPlayed = hours,
                releaseNotificationsEnabled = releaseNotificationsEnabled,
            ),
            game = Game(
                id = game.id,
                name = game.name.trim(),
                coverUrl = game.coverUrl,
                rating = game.rating,
                releaseDateEpochSeconds = game.releaseDateEpochSeconds,
                summary = game.summary,
                genres = game.genres,
                platforms = game.platforms,
            ),
        )
    }
}
