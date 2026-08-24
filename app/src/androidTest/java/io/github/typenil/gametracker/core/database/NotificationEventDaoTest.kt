package io.github.typenil.gametracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.NotificationEventDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.NotificationEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NotificationEventDaoTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDao: GameDao
    private lateinit var notificationEventDao: NotificationEventDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = database.gameDao()
        notificationEventDao = database.notificationEventDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    private fun createTestGame(id: Long = 100L): GameEntity {
        return GameEntity(
            id = id,
            name = "Elden Ring",
            coverUrl = "https://example.com/cover.jpg",
            rating = 96.0,
            releaseDateEpochSeconds = 1645747200L,
            summary = "Action RPG",
            genres = listOf("RPG", "Action"),
            platforms = listOf("PC", "PS5"),
            cachedAtEpochSeconds = 1000L
        )
    }

    @Test
    fun hasEvent_returnsFalseWhenEmpty_andTrueAfterUpsert() = runTest {
        gameDao.upsertGames(listOf(createTestGame(100L)))

        val eventKey = "RELEASE_TODAY_100_1645747200"
        assertFalse(notificationEventDao.hasEvent(eventKey))

        val event = NotificationEventEntity(
            eventKey = eventKey,
            gameId = 100L,
            eventType = "RELEASE_TODAY",
            releaseDateEpochSeconds = 1645747200L,
            notifiedAtEpochSeconds = 2000L
        )
        notificationEventDao.upsertEvent(event)

        assertTrue(notificationEventDao.hasEvent(eventKey))
        val retrieved = notificationEventDao.getEvent(eventKey)
        assertNotNull(retrieved)
        assertEquals("RELEASE_TODAY", retrieved?.eventType)
        assertEquals(100L, retrieved?.gameId)
    }

    @Test
    fun getEventsForGame_returnsEventsOrderedByNotifiedAtDesc() = runTest {
        gameDao.upsertGames(listOf(createTestGame(100L)))

        val event1 = NotificationEventEntity(
            eventKey = "RELEASE_SOON_100_1645747200",
            gameId = 100L,
            eventType = "RELEASE_SOON",
            releaseDateEpochSeconds = 1645747200L,
            notifiedAtEpochSeconds = 1000L
        )
        val event2 = NotificationEventEntity(
            eventKey = "RELEASE_TODAY_100_1645747200",
            gameId = 100L,
            eventType = "RELEASE_TODAY",
            releaseDateEpochSeconds = 1645747200L,
            notifiedAtEpochSeconds = 2000L
        )
        notificationEventDao.upsertEvent(event1)
        notificationEventDao.upsertEvent(event2)

        val events = notificationEventDao.getEventsForGame(100L)
        assertEquals(2, events.size)
        assertEquals("RELEASE_TODAY", events[0].eventType)
        assertEquals("RELEASE_SOON", events[1].eventType)
    }

    @Test
    fun deleteOldEvents_removesEventsOlderThanThreshold() = runTest {
        gameDao.upsertGames(listOf(createTestGame(100L)))

        val oldEvent = NotificationEventEntity(
            eventKey = "OLD_EVENT_100",
            gameId = 100L,
            eventType = "RELEASE_TODAY",
            releaseDateEpochSeconds = 1000L,
            notifiedAtEpochSeconds = 500L
        )
        val freshEvent = NotificationEventEntity(
            eventKey = "FRESH_EVENT_100",
            gameId = 100L,
            eventType = "RELEASE_TODAY",
            releaseDateEpochSeconds = 2000L,
            notifiedAtEpochSeconds = 1500L
        )
        notificationEventDao.upsertEvent(oldEvent)
        notificationEventDao.upsertEvent(freshEvent)

        val deletedCount = notificationEventDao.deleteOldEvents(thresholdEpochSeconds = 1000L)
        assertEquals(1, deletedCount)

        assertFalse(notificationEventDao.hasEvent("OLD_EVENT_100"))
        assertTrue(notificationEventDao.hasEvent("FRESH_EVENT_100"))
    }

    @Test
    fun cascadeDelete_gameDeletionRemovesAssociatedNotificationEvents() = runTest {
        gameDao.upsertGames(listOf(createTestGame(100L)))

        val event = NotificationEventEntity(
            eventKey = "RELEASE_TODAY_100_1645747200",
            gameId = 100L,
            eventType = "RELEASE_TODAY",
            releaseDateEpochSeconds = 1645747200L,
            notifiedAtEpochSeconds = 2000L
        )
        notificationEventDao.upsertEvent(event)
        assertTrue(notificationEventDao.hasEvent("RELEASE_TODAY_100_1645747200"))

        gameDao.deleteGame(100L)
        assertFalse(notificationEventDao.hasEvent("RELEASE_TODAY_100_1645747200"))
    }
}
