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

    /**
     * The two unknown-date labels are intentionally different: details covers
     * historical/incomplete catalog records, notifications track a TBD release.
     */
    @Test
    fun unknownDateLabels_matchTheirContexts() {
        assertEquals(
            "Дата неизвестна",
            russianResources.getString(R.string.details_date_unknown),
        )
        assertEquals(
            "Дата выхода не объявлена",
            russianResources.getString(R.string.notification_date_tbd),
        )
    }

    @Test
    fun releaseNotificationControls_areTranslatedAndAvoidDeliveryPromises() {
        assertEquals(
            "Уведомления о релизе",
            russianResources.getString(R.string.library_release_notifications),
        )
        assertEquals(
            "Сообщать об изменениях даты и приближении релиза. Время доставки приблизительное.",
            russianResources.getString(R.string.library_release_notifications_subtitle),
        )
        assertEquals(
            "Уведомления о релизе включены",
            russianResources.getString(R.string.library_release_notifications_enabled_desc),
        )
        assertEquals(
            "Системные уведомления выключены. Ваш выбор всё равно сохранится.",
            russianResources.getString(R.string.library_release_notifications_permission_hint),
        )
    }
}
