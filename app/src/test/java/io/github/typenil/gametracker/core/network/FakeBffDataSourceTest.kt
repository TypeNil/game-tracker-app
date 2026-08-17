package io.github.typenil.gametracker.core.network

import io.github.typenil.gametracker.core.network.datasource.FakeBffDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FakeBffDataSourceTest {

    private lateinit var fakeDataSource: FakeBffDataSource

    @Before
    fun setUp() {
        fakeDataSource = FakeBffDataSource()
    }

    @Test
    fun `getTopRatedGames returns games with local offline assets`() = runTest {
        val games = fakeDataSource.getTopRatedGames(limit = 5, offset = 0)

        assertEquals(5, games.size)
        assertTrue(games.all { it.coverUrl?.startsWith("file:///android_asset/") == true })
    }

    @Test
    fun `getTopRatedGames rejects non-positive limit or negative offset`() = runTest {
        try {
            fakeDataSource.getTopRatedGames(limit = 0, offset = 0)
            fail("Expected IllegalArgumentException for zero limit")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        try {
            fakeDataSource.getTopRatedGames(limit = 10, offset = -1)
            fail("Expected IllegalArgumentException for negative offset")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `searchGames rejects blank queries`() = runTest {
        try {
            fakeDataSource.searchGames(query = "   ", limit = 10, offset = 0)
            fail("Expected IllegalArgumentException for blank query")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `searchGames finds games case-insensitively`() = runTest {
        val results = fakeDataSource.searchGames(query = "witcher", limit = 10, offset = 0)

        assertEquals(1, results.size)
        assertEquals("The Witcher 3: Wild Hunt", results.first().name)
    }

    @Test
    fun `getGameDetails returns game by ID or throws when not found`() = runTest {
        val game = fakeDataSource.getGameDetails(1942L)
        assertEquals("The Witcher 3: Wild Hunt", game.name)

        try {
            fakeDataSource.getGameDetails(999999L)
            fail("Expected NoSuchElementException for unknown game id")
        } catch (_: NoSuchElementException) {
            // Expected
        }
    }
}
