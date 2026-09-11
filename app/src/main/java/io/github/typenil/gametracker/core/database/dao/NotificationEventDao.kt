package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.NotificationEventEntity

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

    @Query("SELECT COUNT(*) FROM notification_events")
    suspend fun countEvents(): Int

    @Query("DELETE FROM notification_events")
    suspend fun clearAllEvents(): Int
}
