package io.github.typenil.gametracker.core.model

/**
 * Composite domain model combining game metadata with personal library record.
 * Free of Android/Compose/Room dependencies.
 *
 * [developerName] comes from cached game_details when present; null if the
 * user has never hydrated details for this id.
 */
data class LibraryGame(
    val game: Game,
    val entry: LibraryEntry,
    val developerName: String? = null,
    val bannerUrl: String? = null,
)
