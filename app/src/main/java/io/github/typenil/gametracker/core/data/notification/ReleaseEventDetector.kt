package io.github.typenil.gametracker.core.data.notification

import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Pure domain detector evaluating release events for tracked games based on timestamps and release date changes.
 */
object ReleaseEventDetector {

    const val SOON_WINDOW_DAYS_MIN = 1L
    const val SOON_WINDOW_DAYS_MAX = 7L
    private const val SECONDS_PER_DAY = 86_400L

    /**
     * Start of the UTC day containing [epochSeconds], using floor division so pre-epoch clock
     * values (a misconfigured device) still land on the boundary of their own day.
     */
    fun utcDayStartEpochSeconds(epochSeconds: Long): Long =
        Math.floorDiv(epochSeconds, SECONDS_PER_DAY) * SECONDS_PER_DAY

    /**
     * Whole UTC days until [releaseDateEpochSeconds]; negative once that date has passed.
     *
     * Single definition of the release window so [detectEvents] and [isReleasePending] cannot drift.
     */
    fun daysUntilRelease(
        nowEpochSeconds: Long,
        releaseDateEpochSeconds: Long,
        zoneId: ZoneId = ZoneOffset.UTC
    ): Long = ChronoUnit.DAYS.between(
        Instant.ofEpochSecond(nowEpochSeconds).atZone(zoneId).toLocalDate(),
        Instant.ofEpochSecond(releaseDateEpochSeconds).atZone(zoneId).toLocalDate()
    )

    /**
     * True while a release notification can still fire for this game: unknown dates (TBA) and
     * today-or-later. Already-released games are excluded, so the worker stops tracking them and
     * the UI stops offering a switch that could never do anything.
     */
    fun isReleasePending(
        nowEpochSeconds: Long,
        releaseDateEpochSeconds: Long?,
        zoneId: ZoneId = ZoneOffset.UTC
    ): Boolean = releaseDateEpochSeconds == null ||
        daysUntilRelease(nowEpochSeconds, releaseDateEpochSeconds, zoneId) >= 0

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
        val daysUntil = currentReleaseDate?.let {
            daysUntilRelease(nowEpochSeconds, it, zoneId)
        }

        // 1. DATE_CHANGED is only meaningful while the release is still ahead of us: a catalog
        // correction for an already-released game is not something the user asked to hear about.
        val bothDatesKnown = previousReleaseDate != null && currentReleaseDate != null
        val releaseStillPending = daysUntil != null && daysUntil >= 0L
        if (bothDatesKnown && previousReleaseDate != currentReleaseDate && releaseStillPending) {
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
        if (currentReleaseDate != null && daysUntil != null) {
            when {
                daysUntil == 0L -> {
                    events.add(
                        ReleaseEvent(
                            gameId = gameId,
                            gameName = gameName,
                            eventType = NotificationEventType.RELEASE_TODAY,
                            releaseDateEpochSeconds = currentReleaseDate
                        )
                    )
                }
                daysUntil in SOON_WINDOW_DAYS_MIN..SOON_WINDOW_DAYS_MAX -> {
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
