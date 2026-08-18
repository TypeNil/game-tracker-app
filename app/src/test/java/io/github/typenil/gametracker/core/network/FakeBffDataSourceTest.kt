package io.github.typenil.gametracker.core.network

import android.content.Context
import android.content.res.AssetManager
import io.github.typenil.gametracker.core.network.datasource.FakeBffDataSource
import io.github.typenil.gametracker.core.network.di.NetworkModule
import io.github.typenil.gametracker.core.network.model.GameDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class FakeBffDataSourceTest {

    private lateinit var fakeDataSource: FakeBffDataSource
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private val json = NetworkModule.provideJson()

    @Before
    fun setUp() {
        context = mockk()
        assetManager = mockk()

        val assetsDir = System.getProperty("demoAssetsDir")
            ?: throw IllegalStateException("System property 'demoAssetsDir' is missing. Run via Gradle.")

        val fixtureFile = File(assetsDir, "fixtures/v1/games.json")
        if (!fixtureFile.exists()) {
            throw java.lang.IllegalStateException("Fixture file not found at ${fixtureFile.absolutePath}")
        }

        every { context.assets } returns assetManager
        every { assetManager.open("fixtures/v1/games.json") } answers { fixtureFile.inputStream() }

        fakeDataSource = FakeBffDataSource(context, json)
    }

    @Test
    fun `fixture strictly matches Android GameDto serialization contract`() {
        val assetsDir = System.getProperty("demoAssetsDir")!!
        val fixtureFile = File(assetsDir, "fixtures/v1/games.json")
        val jsonString = fixtureFile.readText()

        // Strict JSON parser configuration
        val strictJson = Json {
            ignoreUnknownKeys = false // FAIL on unknown keys!
            coerceInputValues = false // FAIL on wrong types!
            isLenient = false
        }

        // Validate exact keys for every object to prevent silent default-value fallbacks
        val expectedKeys = setOf(
            "id", "name", "coverUrl", "rating", "releaseDateEpochSeconds", "summary", "genres", "platforms"
        )
        val elements = strictJson.parseToJsonElement(jsonString).jsonArray
        elements.forEachIndexed { index, element ->
            assertEquals(
                "Unexpected contract keys in fixture item $index",
                expectedKeys,
                element.jsonObject.keys
            )
        }

        // Will throw SerializationException if contract deviates (e.g. unknown keys, nulls in non-nullable)
        val parsed: List<GameDto> = strictJson.decodeFromString(jsonString)

        // Assert exactly 10 records
        assertEquals(10, parsed.size)
        
        // Assert invariants across all records
        assertTrue(parsed.all { it.coverUrl?.startsWith("file:///android_asset/covers/") == true })
        assertTrue(parsed.all { it.releaseDateEpochSeconds != null })
        assertTrue(parsed.all { it.genres.isNotEmpty() })
        assertTrue(parsed.all { it.platforms.isNotEmpty() })
        assertEquals(parsed.size, parsed.map(GameDto::id).distinct().size)

        // Representative non-default assertions
        val witcher = parsed.first { it.id == 1942L }
        assertEquals("The Witcher 3: Wild Hunt", witcher.name)
        assertEquals(1431993600L, witcher.releaseDateEpochSeconds)
        assertTrue(witcher.coverUrl?.startsWith("file:///android_asset/covers/") == true)
        assertTrue(witcher.genres.contains("Adventure"))
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
