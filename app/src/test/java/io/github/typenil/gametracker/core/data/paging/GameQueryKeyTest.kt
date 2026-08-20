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
    fun `Search includes sort and platform when provided`() {
        val search = GameQueryKey.Search(
            query = "Witcher",
            sort = "RATING_DESC",
            platform = 48
        )
        assertEquals("q:witcher|sort=rating_desc|platform=48", search.key)
    }
}
