package io.github.typenil.gametracker.core.data.notification

import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Pure domain detector evaluating release events for tracked games based on timestamps and release date changes.
 */
object ReleaseEventDetector {

    const val SOON_WINDOW_DAYS_MIN = 1L
    const val SOON_WINDOW_DAYS_MAX = 7L

    /**
     * Evaluates whether [currentReleaseDate] triggers any [ReleaseEvent] relative to [nowEpochSeconds]
     * or compared to [previousReleaseDate].
     *
     * Evaluation defaults to UTC date boundaries since canonical release timestamps are date-level epoch values.
     */
    fun detectEvents(
        nowEpochSeconds: Long,
        gameId: Long,
        gameName: String,
        previousReleaseDate: Long?,
        currentReleaseDate: Long?,
        zoneId: ZoneId = ZoneOffset.UTC
    ): List<ReleaseEvent> {
        val events = mutableListOf<ReleaseEvent>()

        // 1. Check for DATE_CHANGED: previous date was known and differs from new known date
        if (previousReleaseDate != null && currentReleaseDate != null && previousReleaseDate != currentReleaseDate) {
            events.add(
                ReleaseEvent(
                    gameId = gameId,
                    gameName = gameName,
                    eventType = NotificationEventType.DATE_CHANGED,
                    releaseDateEpochSeconds = currentReleaseDate,
                    oldReleaseDateEpochSeconds = previousReleaseDate
                )
            )
        }

        // 2. Check for RELEASE_TODAY and RELEASE_SOON
        if (currentReleaseDate != null) {
            val today = LocalDate.ofInstant(Instant.ofEpochSecond(nowEpochSeconds), zoneId)
            val releaseDate = LocalDate.ofInstant(Instant.ofEpochSecond(currentReleaseDate), zoneId)
            val daysUntilRelease = ChronoUnit.DAYS.between(today, releaseDate)

            when {
                daysUntilRelease == 0L -> {
                    events.add(
                        ReleaseEvent(
                            gameId = gameId,
                            gameName = gameName,
                            eventType = NotificationEventType.RELEASE_TODAY,
                            releaseDateEpochSeconds = currentReleaseDate
                        )
                    )
                }
                daysUntilRelease in SOON_WINDOW_DAYS_MIN..SOON_WINDOW_DAYS_MAX -> {
                    events.add(
                        ReleaseEvent(
                            gameId = gameId,
                            gameName = gameName,
                            eventType = NotificationEventType.RELEASE_SOON,
                            releaseDateEpochSeconds = currentReleaseDate
                        )
                    )
                }
            }
        }

        return events
    }
}
