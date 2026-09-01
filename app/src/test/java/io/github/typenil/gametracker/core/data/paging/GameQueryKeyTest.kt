package io.github.typenil.gametracker.core.data.paging

import org.junit.Assert.assertEquals
import org.junit.Test

class GameQueryKeyTest {

    @Test
    fun `DiscoverTopRated produces canonical key without parameters`() {
        assertEquals("discover:top-rated", GameQueryKey.DiscoverTopRated.key)
    }

    @Test
    fun `Search normalizes query with NFC, trim, and lowercase`() {
        // "é" in NFC (\u00E9) vs NFD ("e" + \u0301)
        val composed = "\u00E9"
        val decomposed = "e\u0301"

        val keyComposed = GameQueryKey.search(composed)
        val keyDecomposed = GameQueryKey.search(decomposed)

        org.junit.Assert.assertTrue(keyComposed.startsWith("search:v3|q=1:é"))
        org.junit.Assert.assertTrue(keyDecomposed.startsWith("search:v3|q=1:é"))
        assertEquals(keyComposed, keyDecomposed)
    }

    @Test
    fun `Search produces clean versioned key`() {
        val key = GameQueryKey.search("  Cyberpunk 2077  ")
        org.junit.Assert.assertTrue(key.startsWith("search:v3|q=14:cyberpunk 2077"))
    }

    @Test
    fun `Search includes filters in canonical key and ignores sort when query is present`() {
        val searchWithQuery = GameQueryKey.Search(
            query = "Witcher",
            genres = listOf("Role-playing (RPG)", "Adventure"),
            platforms = listOf("PC (Microsoft Windows)"),
            minRating = 80,
            minYear = 2020,
            maxYear = 2024,
            sort = "rating",
        )
        val expectedKey = "search:v3|q=7:witcher|genres=32:9:adventure18:role-playing (rpg)" +
            "|platforms=25:22:pc (microsoft windows)|minRating=80|minYear=2020|maxYear=2024|sort="
        assertEquals(expectedKey, searchWithQuery.key)
    }

    @Test
    fun `Search includes sort in canonical key when query is blank`() {
        val catalogSearch = GameQueryKey.Search(
            query = "",
            genres = listOf("RPG"),
            sort = "rating",
        )
        assertEquals(
            "search:v3|q=0:|genres=5:3:rpg|platforms=0:|minRating=|minYear=|maxYear=|sort=rating",
            catalogSearch.key,
        )
    }

    @Test
    fun `Search cannot collide across embedded delimiters and query text`() {
        val rawQueryWithInjectedGenre = GameQueryKey.Search(
            query = "Witcher|genres=rpg",
            genres = emptyList(),
        )
        val searchWithSeparateGenre = GameQueryKey.Search(
            query = "Witcher",
            genres = listOf("rpg"),
        )

        org.junit.Assert.assertNotEquals(rawQueryWithInjectedGenre.key, searchWithSeparateGenre.key)
    }
}
