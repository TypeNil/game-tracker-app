package io.github.typenil.gametracker.core.model

/**
 * Pure domain model representing a user's library record for a game.
 * Free of Android/Compose/Room dependencies.
 */
data class LibraryEntry(
    val gameId: Long,
    val status: LibraryStatus,
    val userRating: Int? = null,
    val userNotes: String? = null,
    val isFavorite: Boolean = false,
    val addedAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val hoursPlayed: Int = 0,
    /**
     * Explicit user intent to be notified about this game's release.
     * Deliberately independent of [status]: library status describes where the game
     * sits in the user's collection, not whether its release should be watched.
     */
    val releaseNotificationsEnabled: Boolean = false
)
