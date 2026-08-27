package io.github.typenil.gametracker.feature.library

import io.github.typenil.gametracker.feature.library.component.formatLibraryAddedDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class LibraryAddedDateFormatTest {

    @Test
    fun formatLibraryAddedDate_staysEnglishWhenDeviceLocaleIsRussian() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals(
                "Nov 14, 2023",
                formatLibraryAddedDate(1_700_000_000L, ZoneOffset.UTC),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
