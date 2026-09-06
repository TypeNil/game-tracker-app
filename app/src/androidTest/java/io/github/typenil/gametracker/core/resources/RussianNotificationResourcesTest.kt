package io.github.typenil.gametracker.core.resources

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.typenil.gametracker.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Guards Russian notification copy that string-parity scripts cannot judge:
 * game titles are grammatical subjects, so verbs must not assume a gender.
 */
@RunWith(AndroidJUnit4::class)
class RussianNotificationResourcesTest {

    private val russianResources by lazy {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru-RU"))
        }
        targetContext.createConfigurationContext(configuration).resources
    }

    @Test
    fun releaseTodayBody_isGenderNeutral() {
        assertEquals(
            "Сегодня состоялся релиз «Hades»!",
            russianResources.getString(R.string.notification_release_today_body, "Hades"),
        )
    }
}
