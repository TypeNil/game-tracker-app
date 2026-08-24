package io.github.typenil.gametracker.core.notification

import android.content.Context
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure helpers for formatting release notification copy, deep link URIs, and notification IDs.
 */
object ReleaseNotificationPayload {

    private const val HASH_MULTIPLIER = 31
    private const val POSITIVE_INTEGER_MASK = 0x7FFFFFFF

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneOffset.UTC)

    fun buildDeepLinkUri(gameId: Long): String {
        return "gametracker://game/$gameId"
    }

    fun computeNotificationId(gameId: Long, eventType: NotificationEventType): Int {
        val hash = gameId.hashCode() * HASH_MULTIPLIER + eventType.hashCode()
        return hash and POSITIVE_INTEGER_MASK
    }

    fun formatDate(epochSeconds: Long?): String {
        if (epochSeconds == null) return "TBD"
        return dateFormatter.format(Instant.ofEpochSecond(epochSeconds))
    }

    fun getTitle(context: Context, event: ReleaseEvent): String {
        return when (event.eventType) {
            NotificationEventType.RELEASE_TODAY -> context.getString(R.string.notification_release_today_title)
            NotificationEventType.RELEASE_SOON -> context.getString(R.string.notification_release_soon_title)
            NotificationEventType.DATE_CHANGED -> context.getString(R.string.notification_date_changed_title)
        }
    }

    fun getBody(context: Context, event: ReleaseEvent): String {
        return when (event.eventType) {
            NotificationEventType.RELEASE_TODAY -> {
                context.getString(R.string.notification_release_today_body, event.gameName)
            }
            NotificationEventType.RELEASE_SOON -> {
                val formattedDate = formatDate(event.releaseDateEpochSeconds)
                context.getString(R.string.notification_release_soon_body, event.gameName, formattedDate)
            }
            NotificationEventType.DATE_CHANGED -> {
                val formattedDate = formatDate(event.releaseDateEpochSeconds)
                context.getString(R.string.notification_date_changed_body, event.gameName, formattedDate)
            }
        }
    }
}
