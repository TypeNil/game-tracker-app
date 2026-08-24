package io.github.typenil.gametracker.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Pure builders for About/Settings external intents. Context-free so they
 * stay unit-testable; callers launch from an Activity/Compose context.
 */
object SettingsIntents {

    const val IGDB_URL = "https://www.igdb.com"

    fun igdbAttributionIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(IGDB_URL))
    }

    fun appNotificationSettingsIntent(packageName: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
    }
}
