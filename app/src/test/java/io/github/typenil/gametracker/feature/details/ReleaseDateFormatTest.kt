package io.github.typenil.gametracker.feature.details

import io.github.typenil.gametracker.core.model.GameReleaseDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ReleaseDateFormatTest {

    @Test
    fun displayDate_utcMidnightEpoch_keepsCalendarDay() {
        val previousTz = TimeZone.getDefault()
        val previousLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            Locale.setDefault(Locale.US)
            val date = GameReleaseDate(
                platform = "PC",
                dateEpochSeconds = 1_431_993_600L,
            )
            assertEquals("19 May 2015", date.displayDate("TBA", Locale.US))
        } finally {
            TimeZone.setDefault(previousTz)
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun displayDate_withoutInstantOrYear_returnsUnknown() {
        val date = GameReleaseDate(platform = "PC", dateEpochSeconds = null, year = null)
        assertEquals("TBA", date.displayDate("TBA", Locale.US))
    }

    @Test
    fun displayDate_yearOnly_usesYear() {
        val date = GameReleaseDate(platform = "PC", dateEpochSeconds = null, year = 2021)
        assertEquals("2021", date.displayDate("TBA", Locale.US))
    }
}
