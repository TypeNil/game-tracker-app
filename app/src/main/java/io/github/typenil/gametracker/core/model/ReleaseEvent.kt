package io.github.typenil.gametracker.core.model

/**
 * Pure domain model representing a detected release event for a tracked game.
 */
data class ReleaseEvent(
    val gameId: Long,
    val gameName: String,
    val eventType: NotificationEventType,
    val releaseDateEpochSeconds: Long?,
    val oldReleaseDateEpochSeconds: Long? = null
) {
    val eventKey: String
        get() = when (eventType) {
            NotificationEventType.RELEASE_TODAY -> "RELEASE_TODAY_${gameId}_${releaseDateEpochSeconds ?: 0}"
            NotificationEventType.RELEASE_SOON -> "RELEASE_SOON_${gameId}_${releaseDateEpochSeconds ?: 0}"
            NotificationEventType.DATE_CHANGED -> {
                val oldDate = oldReleaseDateEpochSeconds ?: 0
                val newDate = releaseDateEpochSeconds ?: 0
                "DATE_CHANGED_${gameId}_${oldDate}_${newDate}"
            }
        }
}
