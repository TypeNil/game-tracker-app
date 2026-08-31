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

    @Test
    fun `toDetailsDto sorts chronologically before truncating to MAX_RELEASE_DATES`() {
        val releases = (2030 downTo 2017).mapIndexed { index, year ->
            IgdbReleaseDate(
                id = index.toLong(),
                date = null,
                y = year,
                platform = IgdbNamedExpansion(id = index.toLong(), name = "Platform $year", abbreviation = "P$year"),
            )
        }
        val game = IgdbGame(
            id = 100L,
            name = "Long Lived Game",
            firstReleaseDate = null,
            releaseDates = releases,
        )

        val dto = game.toDetailsDto(ttb = null)

        assertEquals(12, dto.releaseDates.size)
        // Earliest year (2017) must be preserved at index 0
        assertEquals("Platform 2017", dto.releaseDates[0].platform)
        assertEquals(2017, dto.releaseDates[0].year)
        // 12th entry must be 2028 (2017..2028 = 12 items)
        assertEquals("Platform 2028", dto.releaseDates[11].platform)
        assertEquals(2028, dto.releaseDates[11].year)
        // Years 2029 and 2030 must be truncated
        assertEquals(false, dto.releaseDates.any { it.year == 2029 || it.year == 2030 })
    }
}
