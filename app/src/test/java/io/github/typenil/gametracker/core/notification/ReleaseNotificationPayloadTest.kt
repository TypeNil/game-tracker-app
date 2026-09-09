package io.github.typenil.gametracker.core.notification

import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReleaseNotificationPayloadTest {

    @Test
    fun buildDeepLinkUri_formatsExpectedCanonicalScheme() {
        val uri = ReleaseNotificationPayload.buildDeepLinkUri(12345L)
        assertEquals("gametracker://game/12345", uri)
    }

    @Test
    fun computeNotificationId_dateChangedTransitionsWithSameGameHaveDistinctIds() {
        val dateA = 1_000L
        val dateB = 2_000L
        val dateC = 3_000L
        val aToB = ReleaseEvent(
            gameId = 123L,
            gameName = "Game",
            eventType = NotificationEventType.DATE_CHANGED,
            releaseDateEpochSeconds = dateB,
            oldReleaseDateEpochSeconds = dateA,
        )
        val bToC = ReleaseEvent(
            gameId = 123L,
            gameName = "Game",
            eventType = NotificationEventType.DATE_CHANGED,
            releaseDateEpochSeconds = dateC,
            oldReleaseDateEpochSeconds = dateB,
        )

        val idAtoB = ReleaseNotificationPayload.computeNotificationId(aToB)
        val idBtoC = ReleaseNotificationPayload.computeNotificationId(bToC)

        assertTrue(idAtoB != idBtoC)
    }

    @Test
    fun computeNotificationId_releaseSoonDifferentDatesHaveDistinctIds() {
        val date1 = 1_000L
        val date2 = 2_000L
        val soonDate1 = ReleaseEvent(
            gameId = 123L,
            gameName = "Game",
            eventType = NotificationEventType.RELEASE_SOON,
            releaseDateEpochSeconds = date1,
        )
        val soonDate2 = ReleaseEvent(
            gameId = 123L,
            gameName = "Game",
            eventType = NotificationEventType.RELEASE_SOON,
            releaseDateEpochSeconds = date2,
        )

        val id1 = ReleaseNotificationPayload.computeNotificationId(soonDate1)
        val id2 = ReleaseNotificationPayload.computeNotificationId(soonDate2)

        assertTrue(id1 != id2)
    }

    @Test
    fun computeNotificationId_sameEventKeyIsDeterministicPositiveAndMatchesHash() {
        val event = ReleaseEvent(
            gameId = 123L,
            gameName = "Game",
            eventType = NotificationEventType.RELEASE_TODAY,
            releaseDateEpochSeconds = 1_000L,
        )

        val id1 = ReleaseNotificationPayload.computeNotificationId(event)
        val id2 = ReleaseNotificationPayload.computeNotificationId(event)

        assertEquals(id1, id2)
        assertTrue(id1 >= 0)
        assertEquals(event.eventKey.hashCode() and 0x7FFFFFFF, id1)
    }

    @Test
    fun computeNotificationId_differentEventTypesHaveDistinctIds() {
        val today = ReleaseEvent(
            gameId = 123L,
            gameName = "Game",
            eventType = NotificationEventType.RELEASE_TODAY,
            releaseDateEpochSeconds = 1_000L,
        )
        val soon = ReleaseEvent(
            gameId = 123L,
            gameName = "Game",
            eventType = NotificationEventType.RELEASE_SOON,
            releaseDateEpochSeconds = 1_000L,
        )

        val todayId = ReleaseNotificationPayload.computeNotificationId(today)
        val soonId = ReleaseNotificationPayload.computeNotificationId(soon)

        assertTrue(todayId != soonId)
    }

    @Test
    fun formatDate_formatsUtcDateCorrectly() {
        // 2026-08-24 00:00:00 UTC
        val epoch = 1787529600L
        val formatted = ReleaseNotificationPayload.formatDate(epoch, Locale.US, "TBD")
        assertEquals("Aug 24, 2026", formatted)
    }

    @Test
    fun formatDate_usesRequestedLocale() {
        // 2026-08-24 00:00:00 UTC
        val epoch = 1787529600L
        val formatted = ReleaseNotificationPayload.formatDate(
            epoch,
            Locale.forLanguageTag("ru-RU"),
            "TBD",
        )
        assertEquals("24 авг. 2026 г.", formatted)
    }

    @Test
    fun formatDate_returnsFallbackTextForNull() {
        val formatted = ReleaseNotificationPayload.formatDate(null, Locale.US, "TBD")
        assertEquals("TBD", formatted)
    }
}
