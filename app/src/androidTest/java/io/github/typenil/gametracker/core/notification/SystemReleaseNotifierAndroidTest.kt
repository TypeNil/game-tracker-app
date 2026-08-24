package io.github.typenil.gametracker.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemReleaseNotifierAndroidTest {

    private lateinit var context: Context
    private lateinit var notifier: SystemReleaseNotifier

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notifier = SystemReleaseNotifier(context)
    }

    @Test
    fun createNotificationChannels_registersReleasesChannelOnApi26Plus() {
        notifier.createNotificationChannels()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel: NotificationChannel? = notificationManager.getNotificationChannel(SystemReleaseNotifier.CHANNEL_ID_RELEASES)

            assertNotNull(channel)
            assertEquals(SystemReleaseNotifier.CHANNEL_ID_RELEASES, channel?.id)
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel?.importance)
        }
    }

    @Test
    fun postReleaseNotification_handlesEventExecutionSafely() {
        notifier.createNotificationChannels()

        val event = ReleaseEvent(
            gameId = 42L,
            gameName = "Hollow Knight: Silksong",
            eventType = NotificationEventType.RELEASE_TODAY,
            releaseDateEpochSeconds = 1787529600L
        )

        // Verifies calling postReleaseNotification executes without throwing
        val posted = notifier.postReleaseNotification(event)
        // On emulator/test runner without POST_NOTIFICATIONS granted, it gracefully returns false; with permission true
        // Important: never crashes with unhandled exception or missing icon/channel
    }
}
