package io.github.typenil.gametracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a dispatched notification event for release tracking and deduplication.
 */
@Entity(
    tableName = "notification_events",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("gameId")
    ]
)
data class NotificationEventEntity(
    @PrimaryKey
    val eventKey: String,
    val gameId: Long,
    val eventType: String,
    val releaseDateEpochSeconds: Long?,
    val notifiedAtEpochSeconds: Long
)
