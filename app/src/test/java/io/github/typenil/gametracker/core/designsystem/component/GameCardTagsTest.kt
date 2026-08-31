package io.github.typenil.gametracker.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

class GameCardTagsTest {

    @Test
    fun `selectGenreTags limits to two distinct non-blank genres`() {
        val tags = selectGenreTags(
            genres = listOf(" RPG ", "RPG", "", "Adventure", "Strategy"),
        )
        assertEquals(listOf("RPG", "Adventure"), tags)
    }

    @Test
    fun `selectGenreTags formats Role-playing RPG to RPG`() {
        val tags = selectGenreTags(listOf("Role-playing (RPG)", "Adventure"))
        assertEquals(listOf("RPG", "Adventure"), tags)
    }

    @Test
    fun `formatGenreTag converts verbose IGDB genres to compact labels`() {
        assertEquals("RPG", formatGenreTag("Role-playing (RPG)"))
        assertEquals("Hack & Slash", formatGenreTag("Hack and slash/Beat 'em up"))
        assertEquals("TBS", formatGenreTag("Turn-based strategy (TBS)"))
        assertEquals("RTS", formatGenreTag("Real-time strategy (RTS)"))
        assertEquals("MMO", formatGenreTag("Massively Multiplayer Online (MMO)"))
        assertEquals("Card Game", formatGenreTag("Card & Board Game"))
        assertEquals("Visual Novel", formatGenreTag("Visual Novel"))
        assertEquals("Point & Click", formatGenreTag("Point-and-click"))
        assertEquals("Trivia", formatGenreTag("Quiz/Trivia"))
        assertEquals("Action", formatGenreTag("Action"))
    }

    @Test
    fun `selectGenreTags formats Hack and slash correctly`() {
        val tags = selectGenreTags(listOf("RPG", "Hack and slash/Beat 'em up"))
        assertEquals(listOf("RPG", "Hack & Slash"), tags)
    }
    @Test
    fun `selectGenreTags returns empty when input is empty`() {
        assertEquals(emptyList<String>(), selectGenreTags(emptyList()))
    }

    @Test
    fun `resolvePlatformFamilies recognizes canonical wire platforms and aliases`() {
        val families = resolvePlatformFamilies(
            listOf("PlayStation 5", "Xbox Series X|S", "Nintendo Switch", "PC (Microsoft Windows)"),
        )
        assertEquals(
            listOf(PlatformFamily.PLAYSTATION, PlatformFamily.XBOX, PlatformFamily.NINTENDO, PlatformFamily.PC),
            families,
        )
    }

    @Test
    fun `resolvePlatformFamilies deduplicates multiple versions in same family`() {
        val families = resolvePlatformFamilies(
            listOf("PS4", "PS5", "PlayStation 3"),
        )
        assertEquals(listOf(PlatformFamily.PLAYSTATION), families)
    }

    @Test
    fun `resolvePlatformFamilies maintains enum order regardless of input order`() {
        val families = resolvePlatformFamilies(
            listOf("PC", "PlayStation 5"),
        )
        assertEquals(listOf(PlatformFamily.PLAYSTATION, PlatformFamily.PC), families)
    }

    @Test
    fun `resolvePlatformFamilies supports Mac Linux Steam aliases for PC`() {
        assertEquals(listOf(PlatformFamily.PC), resolvePlatformFamilies(listOf("Mac")))
        assertEquals(listOf(PlatformFamily.PC), resolvePlatformFamilies(listOf("Linux")))
        assertEquals(listOf(PlatformFamily.PC), resolvePlatformFamilies(listOf("Steam")))
    }

    @Test
    fun `resolvePlatformFamilies returns empty for empty or unknown platforms`() {
        assertEquals(emptyList<PlatformFamily>(), resolvePlatformFamilies(emptyList()))
        assertEquals(emptyList<PlatformFamily>(), resolvePlatformFamilies(listOf("UnknownPlatform", "BoardGame")))
    }

    @Test
    fun `resolvePlatformFamilies is locale independent`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val families = resolvePlatformFamilies(listOf("NINTENDO SWITCH", "WINDOWS", "PLAYSTATION 5"))
            assertEquals(
                listOf(PlatformFamily.PLAYSTATION, PlatformFamily.NINTENDO, PlatformFamily.PC),
                families,
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun formatReleaseYear_usesProvidedZone() {
        val moscow = ZoneId.of("Europe/Moscow")
        assertEquals("2020", formatReleaseYear(1_577_833_200L, moscow))
        assertEquals("2019", formatReleaseYear(1_577_833_200L, ZoneOffset.UTC))
        assertEquals("2015", formatReleaseYear(1_431_993_600L, ZoneOffset.UTC))
    }

    @Test
    fun formatReleaseYear_defaultIsUtcWhenDeviceIsLosAngeles() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("2021", formatReleaseYear(1_609_459_200L))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

}
