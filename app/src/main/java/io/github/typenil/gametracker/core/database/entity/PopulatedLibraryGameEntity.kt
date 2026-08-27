package io.github.typenil.gametracker.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Relational model uniting a user's library entry with its parent game entity
 * and optional cached details (companies).
 */
data class PopulatedLibraryGameEntity(
    @Embedded
    val entry: LibraryEntryEntity,

    @Relation(
        parentColumn = "gameId",
        entityColumn = "id",
    )
    val game: GameEntity,

    @Relation(
        entity = GameDetailsEntity::class,
        parentColumn = "gameId",
        entityColumn = "gameId",
    )
    val details: List<GameDetailsEntity> = emptyList(),
)
