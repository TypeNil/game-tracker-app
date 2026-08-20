package io.github.typenil.gametracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a cached search query and its metadata.
 */
@Entity(
    tableName = "search_queries",
    indices = [
        Index("lastQueriedAtEpochSeconds")
    ]
)
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val createdAtEpochSeconds: Long,
    val lastQueriedAtEpochSeconds: Long,
    val resultCount: Int
)

/**
 * Cross-reference entity linking a search query to a game, preserving the exact server-ranked position.
 */
@Entity(
    tableName = "search_results",
    primaryKeys = ["query", "gameId"],
    foreignKeys = [
        ForeignKey(
            entity = SearchQueryEntity::class,
            parentColumns = ["query"],
            childColumns = ["query"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("gameId"),
        Index(value = ["query", "position"], unique = true)
    ]
)
data class SearchResultCrossRef(
    val query: String,
    val gameId: Long,
    val position: Int
)
