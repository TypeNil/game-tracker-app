package io.github.typenil.gametracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.typenil.gametracker.core.model.LibraryStatus

/**
 * Room entity representing a user's library record for a game.
 * Uses RESTRICT on delete to protect user collection from accidental cascade wipes.
 */
@Entity(
    tableName = "library_entries",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("status"),
        Index("isFavorite"),
        Index("updatedAtEpochSeconds")
    ]
)
data class LibraryEntryEntity(
    @PrimaryKey val gameId: Long,
    val status: LibraryStatus,
    val userRating: Int? = null,
    val userNotes: String? = null,
    val isFavorite: Boolean = false,
    val addedAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val hoursPlayed: Int = 0,
    /**
     * `defaultValue` mirrors MIGRATION_6_7's `DEFAULT 0` so a freshly created v7
     * database and a migrated one describe the same schema (standard 4.3.8: ADD COLUMN
     * targets the last property).
     */
    @ColumnInfo(defaultValue = "0")
    val releaseNotificationsEnabled: Boolean = false
)
