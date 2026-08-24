package io.github.typenil.gametracker.core.notification

import io.github.typenil.gametracker.core.model.NotificationEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotificationPayloadTest {

    @Test
    fun buildDeepLinkUri_formatsExpectedCanonicalScheme() {
        val uri = ReleaseNotificationPayload.buildDeepLinkUri(12345L)
        assertEquals("gametracker://game/12345", uri)
    }

    @Test
    fun computeNotificationId_isDeterministicAndPositive() {
        val id1 = ReleaseNotificationPayload.computeNotificationId(123L, NotificationEventType.RELEASE_TODAY)
        val id2 = ReleaseNotificationPayload.computeNotificationId(123L, NotificationEventType.RELEASE_TODAY)
        val id3 = ReleaseNotificationPayload.computeNotificationId(123L, NotificationEventType.RELEASE_SOON)

        assertEquals(id1, id2)
        assertTrue(id1 != id3)
        assertTrue(id1 >= 0)
        assertTrue(id3 >= 0)
    }

    @Test
    fun formatDate_formatsUtcDateCorrectly() {
        // 2026-08-24 00:00:00 UTC
        val epoch = 1787529600L
        val formatted = ReleaseNotificationPayload.formatDate(epoch)
        assertEquals("Aug 24, 2026", formatted)
    }

    @Test
    fun formatDate_handlesNullGracefully() {
        val formatted = ReleaseNotificationPayload.formatDate(null)
        assertEquals("TBD", formatted)
    }
}
