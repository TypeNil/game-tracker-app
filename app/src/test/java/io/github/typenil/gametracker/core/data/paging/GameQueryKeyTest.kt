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

        assertEquals("q:é", keyComposed)
        assertEquals("q:é", keyDecomposed)
        assertEquals(keyComposed, keyDecomposed)
    }

    @Test
    fun `Search produces clean key without pipes when sort and platform are omitted`() {
        val key = GameQueryKey.search("  Cyberpunk 2077  ")
        assertEquals("q:cyberpunk 2077", key)
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
        assertEquals(
            "q:witcher|genres=adventure,role-playing (rpg)|platforms=pc (microsoft windows)|minRating=80|minYear=2020|maxYear=2024",
            searchWithQuery.key,
        )
    }

    @Test
    fun `Search includes sort in canonical key when query is blank`() {
        val catalogSearch = GameQueryKey.Search(
            query = "",
            genres = listOf("RPG"),
            sort = "rating",
        )
        assertEquals(
            "q:|genres=rpg|sort=rating",
            catalogSearch.key,
        )
    }
}
