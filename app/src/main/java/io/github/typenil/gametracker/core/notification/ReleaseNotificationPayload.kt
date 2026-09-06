package io.github.typenil.gametracker.core.notification

import android.content.Context
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pure helpers for formatting release notification copy, deep link URIs, and notification IDs.
 */
object ReleaseNotificationPayload {

    private const val HASH_MULTIPLIER = 31
    private const val POSITIVE_INTEGER_MASK = 0x7FFFFFFF

    fun buildDeepLinkUri(gameId: Long): String {
        return "gametracker://game/$gameId"
    }

    fun computeNotificationId(gameId: Long, eventType: NotificationEventType): Int {
        val hash = gameId.hashCode() * HASH_MULTIPLIER + eventType.hashCode()
        return hash and POSITIVE_INTEGER_MASK
    }

    /**
     * Formats a calendar release date for notification copy. The locale and the
     * unknown-date fallback are explicit inputs: no process-global locale is read,
     * so a language change mid-process cannot leak the previous language.
     */
    fun formatDate(
        epochSeconds: Long?,
        locale: Locale,
        unknownDate: String,
    ): String {
        if (epochSeconds == null) return unknownDate
        val date = Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        return DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(date)
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
                val formattedDate = formatDate(
                    epochSeconds = event.releaseDateEpochSeconds,
                    locale = context.resources.configuration.locales[0],
                    unknownDate = context.getString(R.string.notification_date_tbd),
                )
                context.getString(R.string.notification_release_soon_body, event.gameName, formattedDate)
            }
            NotificationEventType.DATE_CHANGED -> {
                val formattedDate = formatDate(
                    epochSeconds = event.releaseDateEpochSeconds,
                    locale = context.resources.configuration.locales[0],
                    unknownDate = context.getString(R.string.notification_date_tbd),
                )
                context.getString(R.string.notification_date_changed_body, event.gameName, formattedDate)
            }
        }
    }
}
