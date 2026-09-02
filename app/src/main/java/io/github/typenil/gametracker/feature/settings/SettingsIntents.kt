package io.github.typenil.gametracker.feature.settings

import android.content.Intent
import android.net.Uri

/**
 * Pure builders for About/Settings external intents. Context-free so they
 * stay unit-testable; callers launch from an Activity/Compose context.
 */
object SettingsIntents {

    const val IGDB_URL = "https://www.igdb.com"
    const val GITHUB_URL = "https://github.com/TypeNil/game-tracker-app"

    fun igdbAttributionIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(IGDB_URL))
    }

    fun gitHubIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
    }
}

