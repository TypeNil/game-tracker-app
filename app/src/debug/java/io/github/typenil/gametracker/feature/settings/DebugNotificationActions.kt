package io.github.typenil.gametracker.feature.settings

import android.content.Context
import android.widget.Toast
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import io.github.typenil.gametracker.core.notification.SystemReleaseNotifier

internal object DebugNotificationActions {
    const val isVisible: Boolean = true

    fun send(context: Context) {
        val notifier = SystemReleaseNotifier(context)
        val posted = notifier.postReleaseNotification(
            ReleaseEvent(
                gameId = 1942L,
                gameName = "The Witcher 3: Wild Hunt",
                eventType = NotificationEventType.RELEASE_TODAY,
                releaseDateEpochSeconds = System.currentTimeMillis() / 1000
            )
        )
        if (posted) {
            Toast.makeText(context, R.string.settings_notifications_test_sent, Toast.LENGTH_SHORT).show()
        }
    }
}
