package io.github.typenil.gametracker.feature.library

import io.github.typenil.gametracker.feature.library.component.formatLibraryAddedDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class LibraryAddedDateFormatTest {

    private val ruLocale = Locale.forLanguageTag("ru-RU")

    @Test
    fun formatLibraryAddedDate_usesRequestedLocale() {
        assertEquals(
            "14 нояб. 2023 г.",
            formatLibraryAddedDate(
                epochSeconds = 1_700_000_000L,
                zoneId = ZoneOffset.UTC,
                locale = ruLocale,
            ),
        )
    }

    @Test
    fun formatLibraryAddedDate_preservesCalendarDateAcrossLocale() {
        assertEquals(
            "Nov 14, 2023",
            formatLibraryAddedDate(
                epochSeconds = 1_700_000_000L,
                zoneId = ZoneOffset.UTC,
                locale = Locale.US,
            ),
        )
    }
}
