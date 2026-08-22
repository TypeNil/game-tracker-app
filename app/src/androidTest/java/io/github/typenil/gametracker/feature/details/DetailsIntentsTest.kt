package io.github.typenil.gametracker.feature.details

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailsIntentsTest {

    @Test
    fun shareIntent_carriesNameAndUrl_insideChooser() {
        val chooser = DetailsIntents.shareIntent(
            name = "The Witcher 3: Wild Hunt",
            url = "https://www.igdb.com/games/the-witcher-3-wild-hunt"
        )

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(target)
        assertEquals(Intent.ACTION_SEND, target?.action)
        assertEquals("text/plain", target?.type)
        assertEquals(
            "The Witcher 3: Wild Hunt — https://www.igdb.com/games/the-witcher-3-wild-hunt",
            target?.getStringExtra(Intent.EXTRA_TEXT)
        )
    }

    @Test
    fun shareIntent_degradesToBareName_whenUrlMissing() {
        val target = DetailsIntents.shareIntent(name = "PapiHop", url = null)
            .getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

        assertEquals(Intent.ACTION_SEND, target?.action)
        assertEquals("PapiHop", target?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun videoIntent_opensYoutubeWatchUrl() {
        val intent = DetailsIntents.videoIntent(videoId = "qIcTM8WXFjk")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("https://www.youtube.com/watch?v=qIcTM8WXFjk"), intent.data)
    }
}
