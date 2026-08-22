package io.github.typenil.gametracker.feature.details

import android.content.Intent
import android.net.Uri

/**
 * Pure builders for external Android intents of the details screen. Kept free of
 * any state or context so they are unit-testable; callers launch them from an
 * Activity context (no FLAG_ACTIVITY_NEW_TASK needed) and handle
 * [android.content.ActivityNotFoundException].
 */
object DetailsIntents {

    /**
     * Share intent with the game name and, when available, its IGDB page.
     * A missing url degrades to the bare name instead of a broken "name — null".
     */
    fun shareIntent(name: String, url: String?): Intent {
        val text = url?.takeIf { it.isNotBlank() }?.let { "$name — $it" } ?: name
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(sendIntent, null)
    }

    /** Opens the trailer in an external player/browser (IGDB video ids are YouTube). */
    fun videoIntent(videoId: String): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$videoId")
        )
    }
}
