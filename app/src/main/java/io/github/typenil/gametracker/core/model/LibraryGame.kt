package io.github.typenil.gametracker.core.model

/**
 * Composite domain model combining game metadata with personal library record.
 * Free of Android/Compose/Room dependencies.
 */
data class LibraryGame(
    val game: Game,
    val entry: LibraryEntry
)
