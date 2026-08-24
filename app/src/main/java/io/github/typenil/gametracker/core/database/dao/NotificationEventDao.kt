package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.NotificationEventEntity

/**
 * Data Access Object for recorded notification events and deduplication.
 */
@Dao
interface NotificationEventDao {

    @Query("SELECT EXISTS(SELECT 1 FROM notification_events WHERE eventKey = :eventKey)")
    suspend fun hasEvent(eventKey: String): Boolean

    @Query("SELECT * FROM notification_events WHERE eventKey = :eventKey")
    suspend fun getEvent(eventKey: String): NotificationEventEntity?

    @Query("SELECT * FROM notification_events WHERE gameId = :gameId ORDER BY notifiedAtEpochSeconds DESC")
    suspend fun getEventsForGame(gameId: Long): List<NotificationEventEntity>

    @Upsert
    suspend fun upsertEvent(event: NotificationEventEntity): Long

    @Query("DELETE FROM notification_events WHERE notifiedAtEpochSeconds < :thresholdEpochSeconds")
    suspend fun deleteOldEvents(thresholdEpochSeconds: Long): Int
}
