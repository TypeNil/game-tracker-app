package io.github.typenil.gametracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation

/**
 * Slice of [GameDetailsEntity] needed to decorate a library row: developer, banner and the
 * cached release date. Deliberately narrow so the library list does not pay for the full row.
 */
data class LibraryGameDetailsSlice(
    @ColumnInfo(name = "gameId")
    val gameId: Long,
    val companies: List<CompanyColumn> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val releaseDateEpochSeconds: Long? = null,
)
/**
 * Relational model uniting a user's library entry with its parent game entity
 * and optional cached details slice.
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
    val details: List<LibraryGameDetailsSlice> = emptyList(),
)
