package io.github.typenil.gametracker.core.database.mapper

import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry

/**
 * Maps a domain [Game] model to a Room [GameEntity].
 */
fun Game.toEntity(
    cachedAtEpochSeconds: Long = System.currentTimeMillis() / 1000
): GameEntity {
    return GameEntity(
        id = this.id,
        name = this.name,
        coverUrl = this.coverUrl,
        rating = this.rating,
        releaseDateEpochSeconds = this.releaseDateEpochSeconds,
        summary = this.summary,
        genres = this.genres,
        platforms = this.platforms,
        cachedAtEpochSeconds = cachedAtEpochSeconds
    )
}

/**
 * Maps a Room [GameEntity] to a pure domain [Game] model.
 */
fun GameEntity.toDomain(): Game {
    return Game(
        id = this.id,
        name = this.name,
        coverUrl = this.coverUrl,
        rating = this.rating,
        releaseDateEpochSeconds = this.releaseDateEpochSeconds,
        summary = this.summary,
        genres = this.genres,
        platforms = this.platforms
    )
}

/**
 * Maps a list of [GameEntity] instances to a list of domain [Game] models.
 */
fun List<GameEntity>.toDomain(): List<Game> {
    return this.map { it.toDomain() }
}

/**
 * Maps a domain [LibraryEntry] to a Room [LibraryEntryEntity].
 */
fun LibraryEntry.toEntity(): LibraryEntryEntity {
    return LibraryEntryEntity(
        gameId = this.gameId,
        status = this.status,
        userRating = this.userRating,
        userNotes = this.userNotes,
        isFavorite = this.isFavorite,
        addedAtEpochSeconds = this.addedAtEpochSeconds,
        updatedAtEpochSeconds = this.updatedAtEpochSeconds,
        hoursPlayed = this.hoursPlayed
    )
}

/**
 * Maps a Room [LibraryEntryEntity] to a pure domain [LibraryEntry] model.
 */
fun LibraryEntryEntity.toDomain(): LibraryEntry {
    return LibraryEntry(
        gameId = this.gameId,
        status = this.status,
        userRating = this.userRating,
        userNotes = this.userNotes,
        isFavorite = this.isFavorite,
        addedAtEpochSeconds = this.addedAtEpochSeconds,
        updatedAtEpochSeconds = this.updatedAtEpochSeconds,
        hoursPlayed = this.hoursPlayed
    )
}
