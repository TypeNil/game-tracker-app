package io.github.typenil.gametracker.core.notification

import android.content.Intent
import android.provider.Settings

/**
 * System intents for notification permission / channel settings.
 * Lives in core so feature.settings is not a dependency of notification hooks.
 */
object NotificationIntents {

    fun appNotificationSettingsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    }
}
