package io.github.typenil.gametracker.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class GameCardTagsTest {

    @Test
    fun `selectCardTags prefers two genres then one platform`() {
        val tags = selectCardTags(
            genres = listOf("RPG", "Adventure", "Strategy"),
            platforms = listOf("PC", "PlayStation 5"),
        )

        assertEquals(listOf("RPG", "Adventure", "PC"), tags)
    }

    @Test
    fun `selectCardTags uses platforms when genres are empty`() {
        val tags = selectCardTags(
            genres = emptyList(),
            platforms = listOf("PC", "Xbox Series X"),
        )

        assertEquals(listOf("PC"), tags)
    }

    @Test
    fun `selectCardTags skips blanks and duplicates`() {
        val tags = selectCardTags(
            genres = listOf(" RPG ", "RPG", "", "Adventure"),
            platforms = listOf("RPG", "PC"),
        )

        assertEquals(listOf("RPG", "Adventure", "PC"), tags)
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
