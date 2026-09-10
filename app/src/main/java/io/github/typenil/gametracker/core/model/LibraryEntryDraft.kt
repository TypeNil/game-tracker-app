package io.github.typenil.gametracker.core.model

/**
 * User-editable half of a [LibraryEntry], as produced by the library edit sheet.
 *
 * Exists as a named payload instead of a widening positional parameter list so that the
 * adjacent booleans ([isFavorite], [releaseNotificationsEnabled]) cannot be swapped silently
 * at any call site, and so upcoming per-game notification preferences extend one type
 * rather than every screen, ViewModel and repository signature.
 *
 * Intentionally has no default values: every save site must state all six fields.
 * Persistence metadata ([LibraryEntry.addedAtEpochSeconds], [LibraryEntry.updatedAtEpochSeconds])
 * is owned by the repository, not by the draft.
 */
data class LibraryEntryDraft(
    val status: LibraryStatus,
    val userRating: Int?,
    val hoursPlayed: Int,
    val userNotes: String?,
    val isFavorite: Boolean,
    val releaseNotificationsEnabled: Boolean,
)
