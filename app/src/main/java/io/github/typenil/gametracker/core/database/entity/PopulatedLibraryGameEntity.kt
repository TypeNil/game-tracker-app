package io.github.typenil.gametracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation

/**
 * Companies and screenshots columns from [GameDetailsEntity] without the rest of the details row.
 */
data class GameDetailsCompanies(
    @ColumnInfo(name = "gameId")
    val gameId: Long,
    val companies: List<CompanyColumn> = emptyList(),
    val screenshots: List<String> = emptyList(),
)
/**
 * Relational model uniting a user's library entry with its parent game entity
 * and optional cached details companies.
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
    val details: List<GameDetailsCompanies> = emptyList(),
)
