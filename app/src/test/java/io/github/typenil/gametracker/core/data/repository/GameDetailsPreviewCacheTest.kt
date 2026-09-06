package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GameDetailsPreviewCacheTest {

    private val cache = GameDetailsPreviewCache()

    @Test
    fun hydratedEntry_isNotDowngradedByCatalogPreview() {
        val gameId = 42L
        val fullHydrated = GameDetails(
            id = gameId,
            name = "The Witcher 3: Wild Hunt",
            summary = "Geralt of Rivia...",
            screenshots = listOf("https://example.com/shot1.jpg"),
            genres = listOf("RPG", "Adventure"),
            platforms = listOf("PC", "PlayStation 5")
        )
        cache.putHydrated(fullHydrated)

        assertEquals(PreviewQuality.HYDRATED, cache.getQuality(gameId))
        assertEquals(fullHydrated.screenshots, cache.get(gameId)?.screenshots)

        // Attempt to overwrite with a catalog Game model (which has empty screenshots/videos)
        val catalogGame = Game(
            id = gameId,
            name = "The Witcher 3: Wild Hunt (Catalog)",
            summary = "Short description",
            genres = listOf("RPG"),
            platforms = listOf("PC")
        )
        cache.putPreview(catalogGame)

        // Quality must remain HYDRATED and full data (e.g. screenshots) must not be lost
        assertEquals(PreviewQuality.HYDRATED, cache.getQuality(gameId))
        val cached = cache.get(gameId)
        assertNotNull(cached)
        assertEquals("The Witcher 3: Wild Hunt", cached?.name)
        assertEquals(listOf("https://example.com/shot1.jpg"), cached?.screenshots)
    }

    @Test
    fun catalogEntry_isUpgradedByHydratedDetails() {
        val gameId = 100L
        val catalogGame = Game(
            id = gameId,
            name = "Cyberpunk 2077"
        )
        cache.putPreview(catalogGame)

        assertEquals(PreviewQuality.CATALOG, cache.getQuality(gameId))
        assertTrue(cache.get(gameId)?.screenshots.isNullOrEmpty())

        val fullHydrated = GameDetails(
            id = gameId,
            name = "Cyberpunk 2077",
            screenshots = listOf("https://example.com/cp1.jpg")
        )
        cache.putHydrated(fullHydrated)

        assertEquals(PreviewQuality.HYDRATED, cache.getQuality(gameId))
        assertEquals(listOf("https://example.com/cp1.jpg"), cache.get(gameId)?.screenshots)
    }

    @Test
    fun lruEviction_removesEldestWhenExceedingCapacity() {
        // Cache MAX_ENTRIES is 64
        for (i in 1L..70L) {
            cache.putPreview(Game(id = i, name = "Game $i"))
        }

        // Eldest 6 entries (1..6) should be evicted
        assertNull(cache.get(1L))
        assertNull(cache.get(6L))

        // Newer entries should still exist
        assertNotNull(cache.get(7L))
        assertNotNull(cache.get(70L))
    }

    @Test
    fun concurrentPutsAndGets_propagatesNoWorkerFailure() {
        val threadCount = 8
        val iterationsPerThread = 200
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val futures = (0 until threadCount).map { threadIndex ->
                executor.submit {
                    repeat(iterationsPerThread) { index ->
                        val id = (threadIndex * 1_000 + index).toLong()
                        cache.putPreview(Game(id = id, name = "Game $id"))
                        cache.get(id)
                    }
                }
            }

            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
