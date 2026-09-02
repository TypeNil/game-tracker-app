package io.github.typenil.gametracker.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsIntentsTest {

    @Test
    fun igdbAttributionIntent_opensIgdbRoot() {
        val intent = SettingsIntents.igdbAttributionIntent()

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("https://www.igdb.com"), intent.data)
    }

    @Test
    fun gitHubIntent_opensGitHubRepo() {
        val intent = SettingsIntents.gitHubIntent()

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("https://github.com/TypeNil/game-tracker-app"), intent.data)
    }
}
