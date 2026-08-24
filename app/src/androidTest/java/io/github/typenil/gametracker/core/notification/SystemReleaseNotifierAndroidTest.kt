package io.github.typenil.gametracker.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.BuildConfig
import io.github.typenil.gametracker.MainActivity
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemReleaseNotifierAndroidTest {

    private lateinit var context: Context
    private lateinit var notifier: SystemReleaseNotifier
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifier = SystemReleaseNotifier(context)
        notifier.createNotificationChannels()
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun createNotificationChannels_registersReleasesChannelOnApi26Plus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel: NotificationChannel? =
                notificationManager.getNotificationChannel(SystemReleaseNotifier.CHANNEL_ID_RELEASES)

            assertNotNull(channel)
            assertEquals(SystemReleaseNotifier.CHANNEL_ID_RELEASES, channel?.id)
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel?.importance)
        }
    }

    @Test
    fun postReleaseNotification_handlesEventExecutionSafely() {
        notifier.postReleaseNotification(sampleTodayEvent())
    }

    @Test
    fun postReleaseNotification_deniedPermission_returnsFalseWithoutCrashing() {
        if (notifier.hasNotificationPermission()) {
            return
        }

        val posted = notifier.postReleaseNotification(sampleTodayEvent())

        assertFalse(posted)
        assertTrue(notificationManager.activeNotifications.none { it.packageName == context.packageName })
    }

    @Test
    fun buildTapIntent_targetsGameDetailsDeepLink() {
        val intent = notifier.buildTapIntent(GAME_ID).apply {
            setPackage(BuildConfig.APPLICATION_ID)
        }

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("gametracker://game/$GAME_ID"), intent.data)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(Uri.parse("gametracker://game/$GAME_ID"), activity.intent.data)
            }
        }
    }

    @Test
    fun postReleaseNotification_whenPermitted_postsMetadataAndContentIntent() {
        if (!notifier.hasNotificationPermission()) {
            return
        }

        val posted = notifier.postReleaseNotification(sampleTodayEvent())
        assertTrue(posted)

        val notification = notificationManager.activeNotifications.single().notification
        assertEquals(
            context.getString(R.string.notification_release_today_title),
            notification.extras.getString(Notification.EXTRA_TITLE)
        )
        assertEquals(
            context.getString(R.string.notification_release_today_body, GAME_NAME),
            notification.extras.getString(Notification.EXTRA_TEXT)
        )
        assertNotNull(notification.contentIntent)
    }

    private fun sampleTodayEvent(): ReleaseEvent {
        return ReleaseEvent(
            gameId = GAME_ID,
            gameName = GAME_NAME,
            eventType = NotificationEventType.RELEASE_TODAY,
            releaseDateEpochSeconds = 1787529600L
        )
    }

    private companion object {
        const val GAME_ID = 1942L
        const val GAME_NAME = "The Witcher 3: Wild Hunt"
    }
}
