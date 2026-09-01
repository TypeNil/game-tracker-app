package io.github.typenil.gametracker.feature.details

import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.formatPlatformDisplayName
import io.github.typenil.gametracker.core.designsystem.component.resolvePlatformFamily
import io.github.typenil.gametracker.core.model.GameReleaseDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailsFormattersTest {

    @Test
    fun formatPlatformDisplayName_normalizesTechnicalIgdbStrings() {
        assertEquals("PC", formatPlatformDisplayName("PC (Microsoft Windows)"))
        assertEquals("PC", formatPlatformDisplayName("Microsoft Windows"))
        assertEquals("PC", formatPlatformDisplayName("Windows"))
        assertEquals("Mac", formatPlatformDisplayName("Mac OS"))
        assertEquals("Mac", formatPlatformDisplayName("Macintosh"))
        assertEquals("Xbox Series X|S", formatPlatformDisplayName("Xbox Series X|S"))
        assertEquals("Xbox Series X|S", formatPlatformDisplayName("Xbox Series X/S"))
        assertEquals("Xbox Series X", formatPlatformDisplayName("Xbox Series X"))
        assertEquals("Sega Mega Drive", formatPlatformDisplayName("Sega Genesis / Mega Drive"))
        assertEquals("Web", formatPlatformDisplayName("Web browser"))
        assertEquals("PlayStation 5", formatPlatformDisplayName("PlayStation 5"))
        assertEquals("Nintendo Switch", formatPlatformDisplayName("Nintendo Switch"))
    }

    @Test
    fun resolvePlatformFamily_correctlyIdentifiesFamilies() {
        assertEquals(PlatformFamily.PLAYSTATION, resolvePlatformFamily("PlayStation 5"))
        assertEquals(PlatformFamily.PLAYSTATION, resolvePlatformFamily("PS4"))
        assertEquals(PlatformFamily.PLAYSTATION, resolvePlatformFamily("PS Vita"))
        assertEquals(PlatformFamily.XBOX, resolvePlatformFamily("Xbox Series X|S"))
        assertEquals(PlatformFamily.XBOX, resolvePlatformFamily("Xbox 360"))
        assertEquals(PlatformFamily.NINTENDO, resolvePlatformFamily("Nintendo Switch"))
        assertEquals(PlatformFamily.NINTENDO, resolvePlatformFamily("Wii U"))
        assertEquals(PlatformFamily.NINTENDO, resolvePlatformFamily("Game Boy Advance"))
        assertEquals(PlatformFamily.PC, resolvePlatformFamily("PC"))
        assertEquals(PlatformFamily.PC, resolvePlatformFamily("PC (Microsoft Windows)"))
        assertEquals(PlatformFamily.PC, resolvePlatformFamily("Linux"))
        assertEquals(PlatformFamily.PC, resolvePlatformFamily("Mac"))
        assertEquals(PlatformFamily.PC, resolvePlatformFamily("Steam"))
        assertNull(resolvePlatformFamily("Arcade"))
        assertNull(resolvePlatformFamily("Android"))
        assertNull(resolvePlatformFamily("Sega Genesis / Mega Drive"))
        assertNull(resolvePlatformFamily("Sega Mega Drive"))
        assertNull(resolvePlatformFamily("Commodore / Amiga"))
    }
    @Test
    fun resolveFactsTopology_correctlyDeterminesPlatformsSpan() {
        // 1. Release + Modes + Platforms (3 cards, odd, no time -> full width)
        assertTrue(
            resolveFactsTopology(
                hasRelease = true,
                hasModes = true,
                hasPlatforms = true,
                hasTime = false,
            ).platformsFullWidth
        )

        // 2. Release + Platforms (2 cards, even -> half width)
        assertFalse(
            resolveFactsTopology(
                hasRelease = true,
                hasModes = false,
                hasPlatforms = true,
                hasTime = false,
            ).platformsFullWidth
        )

        // 3. Modes + Platforms (2 cards, even -> half width)
        assertFalse(
            resolveFactsTopology(
                hasRelease = false,
                hasModes = true,
                hasPlatforms = true,
                hasTime = false,
            ).platformsFullWidth
        )

        // 4. Platforms only (1 card, odd, no time -> full width)
        assertTrue(
            resolveFactsTopology(
                hasRelease = false,
                hasModes = false,
                hasPlatforms = true,
                hasTime = false,
            ).platformsFullWidth
        )

        // 5. Release + Modes + Platforms + Time (4 cards, even -> half width)
        assertFalse(
            resolveFactsTopology(
                hasRelease = true,
                hasModes = true,
                hasPlatforms = true,
                hasTime = true,
            ).platformsFullWidth
        )

        // 6. Release + Platforms + Time (3 cards, odd, but Time is last -> half width)
        assertFalse(
            resolveFactsTopology(
                hasRelease = true,
                hasModes = false,
                hasPlatforms = true,
                hasTime = true,
            ).platformsFullWidth
        )

        // 7. No platforms present -> false
        assertFalse(
            resolveFactsTopology(
                hasRelease = true,
                hasModes = true,
                hasPlatforms = false,
                hasTime = false,
            ).platformsFullWidth
        )
    }

    @Test
    fun formatHeaderTagPreview_whenWithinLimit_showsAllTagsWithoutOverflow() {
        val genres = listOf("Role-playing (RPG)", "Adventure")

        val result = formatHeaderTagPreview(genres, emptyList())
        assertEquals(listOf("RPG", "Adventure"), result.previewTags)
        assertEquals(0, result.overflowCount)
    }

    @Test
    fun formatHeaderTagPreview_withThreeTags_showsOneAndTwoOverflow() {
        val genres = listOf("Role-playing (RPG)", "Adventure")
        val themes = listOf("Fantasy")

        val result = formatHeaderTagPreview(genres, themes)
        assertEquals(listOf("RPG"), result.previewTags)
        assertEquals(2, result.overflowCount)
    }

    @Test
    fun formatHeaderTagPreview_withMoreThanThreeTags_showsOneAndOverflow() {
        val genres = listOf("RPG", "Adventure", "Shooter")
        val themes = listOf("Fantasy", "Sci-Fi")

        val result = formatHeaderTagPreview(genres, themes)
        assertEquals(listOf("RPG"), result.previewTags)
        assertEquals(4, result.overflowCount)
    }

    @Test
    fun formatHeaderTagPreview_ignoresBlankAndDuplicateNormalizedTags() {
        val genres = listOf("", "   ", "Role-playing (RPG)")
        val themes = listOf("RPG", "   ", "Fantasy")

        val result = formatHeaderTagPreview(genres, themes)
        assertEquals(listOf("RPG", "Fantasy"), result.previewTags)
        assertEquals(0, result.overflowCount)
    }

    @Test
    fun formatPlatformsPreview_withFourLimit_showsUpToFourPlatformsAndOverflow() {
        val platforms = listOf(
            "PC (Microsoft Windows)",
            "PlayStation 5",
            "Xbox Series X|S",
            "Nintendo Switch",
            "Macintosh",
        )
        val result = formatPlatformsPreview(platforms, limit = PLATFORMS_PREVIEW_LIMIT_FULL)
        assertEquals("PC, PlayStation 5, Xbox Series X|S, Nintendo Switch", result.previewText)
        assertEquals(1, result.overflowCount)
    }

    @Test
    fun formatPlatformsPreview_withHalfLimit_showsTwoPlatformsAndOverflow() {
        val platforms = listOf(
            "PC (Microsoft Windows)",
            "PlayStation 5",
            "Xbox Series X|S",
            "Nintendo Switch",
        )
        val result = formatPlatformsPreview(platforms, limit = PLATFORMS_PREVIEW_LIMIT_HALF)
        assertEquals("PC, PlayStation 5", result.previewText)
        assertEquals(2, result.overflowCount)
    }

    @Test
    fun formatGameModesPreview_shortensModesAndCalculatesOverflow() {
        val modes = listOf("Single player", "Multiplayer", "Co-operative", "Split screen")
        val result = formatGameModesPreview(modes, limit = 2)

        assertEquals("Single-player, Multiplayer", result.previewText)
        assertEquals(2, result.overflowCount)
    }

    @Test
    fun mergePlatformsAndReleases_sortsAndAssignsFallbackDates() {
        val platforms = listOf("PC (Microsoft Windows)", "PlayStation 5", "Nintendo Switch")
        val releaseDates = listOf(
            GameReleaseDate(platform = "PC", dateEpochSeconds = 1700000000L, year = 2023),
            GameReleaseDate(platform = "PlayStation 5", dateEpochSeconds = null, year = 2024),
        )

        val merged = mergePlatformsAndReleases(platforms, releaseDates, unknownDateLabel = "TBA")

        assertEquals(3, merged.size)
        // Dated first
        assertEquals("PC", merged[0].platform)
        assertEquals(releaseDates[0].displayDate("TBA"), merged[0].displayDate)
        // Year-only second
        assertEquals("PlayStation 5", merged[1].platform)
        assertEquals("2024", merged[1].displayDate)

        // Undated/fallback last
        assertEquals("Nintendo Switch", merged[2].platform)
        assertEquals("TBA", merged[2].displayDate)
    }
}
