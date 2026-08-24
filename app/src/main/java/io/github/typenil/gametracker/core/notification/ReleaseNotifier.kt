package io.github.typenil.gametracker.core.notification

import io.github.typenil.gametracker.core.model.ReleaseEvent

/**
 * Interface abstraction for creating notification channels and dispatching system release notifications.
 */
interface ReleaseNotifier {

    /**
     * Registers the release notification channel on supported platform versions (Android 8.0+).
     */
    fun createNotificationChannels()

    /**
     * Checks if the app is currently allowed to post notifications.
     */
    fun hasNotificationPermission(): Boolean

    /**
     * Builds and posts a system notification for the given [event].
     * Returns true if successfully posted, or false if permission is denied.
     */
    fun postReleaseNotification(event: ReleaseEvent): Boolean
}
