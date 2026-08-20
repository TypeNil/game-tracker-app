package io.github.typenil.gametracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a cached video game in the catalog.
 */
@Entity(
    tableName = "games",
    indices = [
        Index("name"),
        Index("rating"),
        Index("releaseDateEpochSeconds")
    ]
)
data class GameEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val coverUrl: String?,
    val rating: Double?,
    val releaseDateEpochSeconds: Long?,
    val summary: String?,
    val genres: List<String>,
    val platforms: List<String>,
    val cachedAtEpochSeconds: Long
)
