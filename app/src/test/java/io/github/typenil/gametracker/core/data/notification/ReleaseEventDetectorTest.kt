package io.github.typenil.gametracker.core.data.notification

import io.github.typenil.gametracker.core.model.NotificationEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ReleaseEventDetectorTest {

    private val testZone = ZoneOffset.UTC
    // 2026-08-24 12:00:00 UTC
    private val nowEpoch = 1787572800L

    @Test
    fun detectEvents_whenReleaseDateIsSameDay_emitsReleaseToday() {
        // 2026-08-24 00:00:00 UTC
        val todayRelease = 1787529600L

        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nowEpoch,
            gameId = 42L,
            gameName = "Hollow Knight: Silksong",
            previousReleaseDate = null,
            currentReleaseDate = todayRelease,
            zoneId = testZone
        )

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(NotificationEventType.RELEASE_TODAY, event.eventType)
        assertEquals(42L, event.gameId)
        assertEquals("RELEASE_TODAY_42_1787529600", event.eventKey)
    }

    @Test
    fun detectEvents_whenReleaseDateIsWithinOneToSevenDays_emitsReleaseSoon() {
        // 2026-08-27 (3 days later)
        val soonRelease = 1787788800L

        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nowEpoch,
            gameId = 42L,
            gameName = "Silksong",
            previousReleaseDate = null,
            currentReleaseDate = soonRelease,
            zoneId = testZone
        )

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(NotificationEventType.RELEASE_SOON, event.eventType)
        assertEquals(42L, event.gameId)
        assertEquals("RELEASE_SOON_42_1787788800", event.eventKey)
    }

    @Test
    fun detectEvents_whenReleaseDateChanged_emitsDateChangedAndReleaseSoonIfApplicable() {
        val oldRelease = 1789000000L // far future
        val newRelease = 1787788800L // 3 days in future

        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nowEpoch,
            gameId = 42L,
            gameName = "Silksong",
            previousReleaseDate = oldRelease,
            currentReleaseDate = newRelease,
            zoneId = testZone
        )

        assertEquals(2, events.size)
        val expectedKey = "DATE_CHANGED_42_${oldRelease}_${newRelease}"
        assertTrue(events.any { it.eventType == NotificationEventType.DATE_CHANGED && it.eventKey == expectedKey })
        assertTrue(events.any { it.eventType == NotificationEventType.RELEASE_SOON })
    }

    @Test
    fun detectEvents_whenReleaseDateIsPast_emitsNoEvent() {
        // 2026-08-20 (4 days ago)
        val pastRelease = 1787184000L

        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nowEpoch,
            gameId = 42L,
            gameName = "Past Game",
            previousReleaseDate = null,
            currentReleaseDate = pastRelease,
            zoneId = testZone
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun detectEvents_whenReleaseDateIsFarFuture_andNotChanged_emitsNoEvent() {
        // 2026-12-01 (more than 7 days later)
        val futureRelease = 1796083200L

        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nowEpoch,
            gameId = 42L,
            gameName = "Future Game",
            previousReleaseDate = futureRelease,
            currentReleaseDate = futureRelease,
            zoneId = testZone
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun detectEvents_whenReleaseDateIsNull_emitsNoEvent() {
        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nowEpoch,
            gameId = 42L,
            gameName = "TBD Game",
            previousReleaseDate = null,
            currentReleaseDate = null,
            zoneId = testZone
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun detectEvents_handlesTimezoneBoundariesCleanly() {
        // Test near UTC midnight
        // 2026-08-24 23:59:50 UTC
        val nearMidnightNow = 1787615990L
        // 2026-08-24 00:00:00 UTC
        val todayRelease = 1787529600L

        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nearMidnightNow,
            gameId = 10L,
            gameName = "Game",
            previousReleaseDate = null,
            currentReleaseDate = todayRelease,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, events.size)
        assertEquals(NotificationEventType.RELEASE_TODAY, events.single().eventType)
    }
}
