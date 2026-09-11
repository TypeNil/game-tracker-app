package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.data.backup.LibraryBackupItem
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryNotes
import io.github.typenil.gametracker.core.model.LibraryStatus

/**
 * Generated games live far above every IGDB id and above the demo fixtures (`900001..900012`), so a
 * seeded game can never collide with catalog or fixture data.
 */
internal const val SYNTHETIC_ID_BASE = 9_000_000_000L
internal const val SECONDS_PER_DAY = 86_400L
internal const val MIN_RATING = 1
internal const val MAX_RATING = 10
internal const val MAX_HOURS = 999_999

internal fun syntheticId(preset: DevSeedPreset, index: Int): Long =
    SYNTHETIC_ID_BASE + preset.syntheticBlock + index

/** Sits exactly on the storage limit, so the boundary is exercised without being clamped away. */
internal val maxLengthNote: String =
    "seed note ".repeat(LibraryNotes.MAX_CODE_POINTS / "seed note ".length + 1).take(LibraryNotes.MAX_CODE_POINTS)

/** One note past the limit, so the import clamp is exercised rather than assumed. */
internal val overLimitNote: String = maxLengthNote + " past the storage limit"

internal fun seedItem(
    game: Game,
    status: LibraryStatus,
    addedAtEpochSeconds: Long,
    updatedAtEpochSeconds: Long,
    rating: Int? = null,
    hours: Int = 0,
    favorite: Boolean = false,
    notes: String? = null,
    notifyOnRelease: Boolean = false,
): LibraryBackupItem = LibraryBackupItem(
    entry = LibraryEntry(
        gameId = game.id,
        status = status,
        userRating = rating,
        userNotes = notes,
        isFavorite = favorite,
        addedAtEpochSeconds = addedAtEpochSeconds,
        updatedAtEpochSeconds = updatedAtEpochSeconds,
        hoursPlayed = hours,
        releaseNotificationsEnabled = notifyOnRelease,
    ),
    game = game,
)

/**
 * Catalog row for a generated game. No remote source can resolve a reserved id, and
 * `GameDetailsViewModel` only hides a failed details refresh when the cached row exists, so the
 * developer tools have to write this cache themselves or every synthetic card opens on an error.
 */
internal fun Game.toSyntheticDetails(): GameDetails = GameDetails(
    id = id,
    name = name,
    coverUrl = coverUrl,
    rating = rating,
    releaseDateEpochSeconds = releaseDateEpochSeconds,
    summary = summary,
    genres = genres,
    platforms = platforms,
)
