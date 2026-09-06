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
 * Verifies Russian plural morphology (one/few/many) for user-visible counts.
 * Structural parity scripts cannot catch wrong form selection, so the exact
 * strings for 1/2/5/21 are asserted against a ru-RU configuration context.
 */
@RunWith(AndroidJUnit4::class)
class RussianPluralsTest {

    private val russianResources by lazy {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru-RU"))
        }
        targetContext.createConfigurationContext(configuration).resources
    }

    @Test
    fun russianFilterPlural_selectsAllRequiredForms() {
        assertEquals(
            "Применить (1 фильтр)",
            russianResources.getQuantityString(R.plurals.search_filter_apply_count, 1, 1),
        )
        assertEquals(
            "Применить (2 фильтра)",
            russianResources.getQuantityString(R.plurals.search_filter_apply_count, 2, 2),
        )
        assertEquals(
            "Применить (5 фильтров)",
            russianResources.getQuantityString(R.plurals.search_filter_apply_count, 5, 5),
        )
        assertEquals(
            "Применить (21 фильтр)",
            russianResources.getQuantityString(R.plurals.search_filter_apply_count, 21, 21),
        )
    }

    @Test
    fun russianResultsPlural_selectsAllRequiredForms() {
        assertEquals(
            "Показан 1 загруженный результат",
            russianResources.getQuantityString(R.plurals.search_results_loaded_count, 1, 1),
        )
        assertEquals(
            "Показано 2 загруженных результата",
            russianResources.getQuantityString(R.plurals.search_results_loaded_count, 2, 2),
        )
        assertEquals(
            "Показано 5 загруженных результатов",
            russianResources.getQuantityString(R.plurals.search_results_loaded_count, 5, 5),
        )
        assertEquals(
            "Показан 21 загруженный результат",
            russianResources.getQuantityString(R.plurals.search_results_loaded_count, 21, 21),
        )
    }
}
