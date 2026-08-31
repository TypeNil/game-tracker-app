package com.gametracker.backend.models

import org.junit.Assert.assertEquals
import org.junit.Test

class GameDtoTest {

    @Test
    fun `toDetailsDto sorts mixed precision release dates chronologically`() {
        val game = IgdbGame(
            id = 100L,
            name = "The Witcher 3",
            firstReleaseDate = 1431993600L, // May 19, 2015
            releaseDates = listOf(
                IgdbReleaseDate(
                    id = 1L,
                    date = null,
                    y = 2026,
                    platform = IgdbNamedExpansion(id = 1L, name = "PlayStation 6", abbreviation = "PS6"),
                ),
                IgdbReleaseDate(
                    id = 2L,
                    date = 1431993600L, // 2015-05-19
                    y = 2015,
                    platform = IgdbNamedExpansion(id = 6L, name = "PC (Microsoft Windows)", abbreviation = "PC"),
                ),
            ),
        )

        val dto = game.toDetailsDto(ttb = null)

        assertEquals(2, dto.releaseDates.size)
        assertEquals("PC", dto.releaseDates[0].platform)
        assertEquals(1431993600L, dto.releaseDates[0].dateEpochSeconds)
        assertEquals(2015, dto.releaseDates[0].year)

        assertEquals("PlayStation 6", dto.releaseDates[1].platform)
        assertEquals(null, dto.releaseDates[1].dateEpochSeconds)
        assertEquals(2026, dto.releaseDates[1].year)
    }
}
