package io.github.typenil.gametracker.core.network

import android.content.Context
import android.content.res.AssetManager
import io.github.typenil.gametracker.core.network.datasource.FakeBffDataSource
import io.github.typenil.gametracker.core.network.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Contract parity for the demo data source against the shared BFF fixture
 * `config/search-contract/search-contract-cases.json`. Backend-driven cases (query escaping and
 * rejection) are enforced by the client [io.github.typenil.gametracker.core.model.SearchInputPolicy]
 * before the data source is reached; here the fake must agree with the BFF on silent normalization
 * of out-of-range parameters (pagination, minRating) instead of throwing.
 */
class FakeBffDataSourceContractTest {

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

        val gamesFixture = File(assetsDir, "fixtures/v1/games.json")
        val detailsFixture = File(assetsDir, "fixtures/v1/game-details.json")
        if (!gamesFixture.exists()) {
            throw IllegalStateException("Fixture file not found at ${gamesFixture.absolutePath}")
        }
        if (!detailsFixture.exists()) {
            throw IllegalStateException("Fixture file not found at ${detailsFixture.absolutePath}")
        }

        every { context.assets } returns assetManager
        every { assetManager.open("fixtures/v1/games.json") } answers { gamesFixture.inputStream() }
        every { assetManager.open("fixtures/v1/game-details.json") } answers { detailsFixture.inputStream() }

        fakeDataSource = FakeBffDataSource(context, json)
    }

    private val fixturesDir: String
        get() = System.getProperty("demoAssetsDir")!!

    private suspend fun search(
        query: String?,
        minRating: Int?,
        limit: Int = 30,
        offset: Int = 0,
    ): List<io.github.typenil.gametracker.core.network.model.GameDto> =
        fakeDataSource.searchGames(
            query = query,
            genres = emptyList(),
            platforms = emptyList(),
            minRating = minRating,
            minYear = null,
            maxYear = null,
            sort = null,
            limit = limit,
            offset = offset,
        )

    @Test
    fun `pagination is clamped silently like the BFF instead of rejected`() = runTest {
        // limit 0 -> clamped to 1; offset -5 -> clamped to 0
        val zeroLimitNegativeOffset = search(query = null, minRating = 0, limit = 0, offset = -5)
        assertEquals(1, zeroLimitNegativeOffset.size)

        // Beyond the fixture end with an over-range offset: empty result, no exception
        val overLimit = search(query = null, minRating = 0, limit = 500, offset = 2000)
        assertTrue(overLimit.isEmpty())
    }

    @Test
    fun `minRating is clamped silently like the BFF instead of rejected`() = runTest {
        val clampedLow = search(query = null, minRating = -10)
        val clampedHigh = search(query = null, minRating = 900)
        // -10 clamps to 0 -> everything rated; 900 clamps to 100 -> only perfect scores.
        // The exact winner set depends on the fixture, so assert the ordering invariant instead.
        assertTrue(clampedLow.size >= clampedHigh.size)
        assertTrue(clampedLow.isNotEmpty())
    }

    @Test
    fun `text search is canonicalized like the BFF before matching`() = runTest {
        val canonicalInput = search(query = "  grand\u00A0theft  ", minRating = null)
        val decomposedInput = search(query = "Ragnaro\u0308k", minRating = null)
        val plainInput = search(query = "Ragnar\u00F6k", minRating = null)
        val plainCanonical = search(query = "Grand Theft Auto", minRating = null)
        // NFC-equivalent inputs must surface the same result set
        assertEquals(decomposedInput, plainInput)
        // NBSP + ragged case must match the same set as the plainly canonical input
        assertEquals(plainCanonical, canonicalInput)
    }

    @Test
    fun `text search never returns coverless games - matches BFF cover policy`() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val games = json.decodeFromString<List<io.github.typenil.gametracker.core.network.model.GameDto>>(
            File(fixturesDir, "fixtures/v1/games.json").readText(),
        )
        val coverless = games.firstOrNull { it.coverUrl == null }
        if (coverless != null) {
            val byName = search(query = coverless.name, minRating = null)
            assertTrue(byName.none { it.coverUrl == null })
        }
    }
}
