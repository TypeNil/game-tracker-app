package io.github.typenil.gametracker.core.network

import android.content.Context
import android.content.res.AssetManager
import io.github.typenil.gametracker.core.network.datasource.FakeBffDataSource
import io.github.typenil.gametracker.core.network.di.NetworkModule
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
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

        val gamesFixture = File(assetsDir, "fixtures/v1/games.json")
        val detailsFixture = File(assetsDir, "fixtures/v1/game-details.json")
        if (!gamesFixture.exists()) {
            throw java.lang.IllegalStateException("Fixture file not found at ${gamesFixture.absolutePath}")
        }
        if (!detailsFixture.exists()) {
            throw java.lang.IllegalStateException("Fixture file not found at ${detailsFixture.absolutePath}")
        }

        every { context.assets } returns assetManager
        every { assetManager.open("fixtures/v1/games.json") } answers { gamesFixture.inputStream() }
        every { assetManager.open("fixtures/v1/game-details.json") } answers { detailsFixture.inputStream() }
        val trendingFixture = File(assetsDir, "fixtures/v1/trending.json")
        if (!trendingFixture.exists()) {
            throw java.lang.IllegalStateException("Fixture file not found at ${trendingFixture.absolutePath}")
        }
        listOf("playing", "wanted", "upcoming", "twitch").forEach { type ->
            val fixture = File(assetsDir, "fixtures/v1/popular-$type.json")
            every { assetManager.open("fixtures/v1/popular-$type.json") } answers { fixture.inputStream() }
        }
        every { assetManager.open("fixtures/v1/trending.json") } answers { trendingFixture.inputStream() }

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
        // Assert exactly 22 records
        assertEquals(22, parsed.size)
        
        // Assert invariants across all records
        assertTrue(parsed.all { it.coverUrl == null || it.coverUrl.startsWith("file:///android_asset/covers/") })
        assertTrue(parsed.all { it.genres.isNotEmpty() })
        assertTrue(parsed.all { it.platforms.isNotEmpty() })
        assertEquals(parsed.size, parsed.map(GameDto::id).distinct().size)

        // Representative non-default assertions
        val witcher = parsed.first { it.id == 1942L }
        assertEquals("The Witcher 3: Wild Hunt", witcher.name)
        assertEquals(1431993600L, witcher.releaseDateEpochSeconds)
        assertTrue(witcher.coverUrl?.startsWith("file:///android_asset/covers/") == true)
        assertTrue(witcher.genres.contains("Adventure"))

        // Edge case fixtures assertions
        val gta = parsed.first { it.id == 900003L }
        assertEquals("Grand Theft Auto VI", gta.name)
        org.junit.Assert.assertNull(gta.rating)
        org.junit.Assert.assertNull(gta.releaseDateEpochSeconds)

        val silk = parsed.first { it.id == 900002L }
        org.junit.Assert.assertNull(silk.summary)

        val noMedia = parsed.first { it.id == 900009L }
        org.junit.Assert.assertNull(noMedia.coverUrl)
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
    fun `details fixture strictly matches GameDetailsDto contract with offline navigation closure`() {
        val assetsDir = System.getProperty("demoAssetsDir")!!
        val detailsString = File(assetsDir, "fixtures/v1/game-details.json").readText()
        val gamesString = File(assetsDir, "fixtures/v1/games.json").readText()

        val strictJson = Json {
            ignoreUnknownKeys = false
            coerceInputValues = false
            isLenient = false
        }

        validateDetailsStructure(strictJson.parseToJsonElement(detailsString).jsonArray)

        val details: List<GameDetailsDto> = strictJson.decodeFromString(detailsString)
        val listGames: List<GameDto> = strictJson.decodeFromString(gamesString)
        assertEquals(22, details.size)
        assertEquals(details.size, details.map(GameDetailsDto::id).distinct().size)
        assertEquals(listGames.map(GameDto::id).toSet(), details.map(GameDetailsDto::id).toSet())
        val detailIds = details.map(GameDetailsDto::id).toSet()
        assertTrue(details.all { game -> game.similarGames.all { it.id in detailIds && it.id != game.id } })
        validateSharedFields(listGames, details)
        validateRailIds(assetsDir, strictJson, detailIds)
        validateWitcherDetails(details)
        validateMediaAndEdgeCases(details)
    }

    private fun validateDetailsStructure(elements: kotlinx.serialization.json.JsonArray) {
        val expectedKeys = setOf(
            "id", "name", "coverUrl", "rating", "releaseDateEpochSeconds", "summary", "genres", "platforms",
            "url", "totalRating", "totalRatingCount", "themes", "gameModes", "releaseDates",
            "companies", "screenshots", "videos", "similarGames",
            "artworkUrl", "timeToBeatMainSeconds", "timeToBeatCompleteSeconds"
        )
        val releaseDateKeys = setOf("platform", "dateEpochSeconds", "year")
        val companyKeys = setOf("name", "isDeveloper", "isPublisher")
        val videoKeys = setOf("videoId", "name")
        val similarKeys = setOf("id", "name", "coverUrl", "totalRating", "genres", "platforms")

        elements.forEachIndexed { index, element ->
            val obj = element.jsonObject
            assertEquals("Unexpected contract keys in details item $index", expectedKeys, obj.keys)
            obj["releaseDates"]!!.jsonArray.forEach { assertEquals(releaseDateKeys, it.jsonObject.keys) }
            obj["companies"]!!.jsonArray.forEach { assertEquals(companyKeys, it.jsonObject.keys) }
            obj["videos"]!!.jsonArray.forEach { assertEquals(videoKeys, it.jsonObject.keys) }
            obj["similarGames"]!!.jsonArray.forEach { assertEquals(similarKeys, it.jsonObject.keys) }
        }
    }

    private fun validateSharedFields(listGames: List<GameDto>, details: List<GameDetailsDto>) {
        listGames.forEach { listGame ->
            val detail = details.first { it.id == listGame.id }
            assertEquals(listGame.name, detail.name)
            assertEquals(listGame.coverUrl, detail.coverUrl)
            assertEquals(listGame.rating, detail.rating)
            assertEquals(listGame.releaseDateEpochSeconds, detail.releaseDateEpochSeconds)
            assertEquals(listGame.summary, detail.summary)
            assertEquals(listGame.genres, detail.genres)
            assertEquals(listGame.platforms, detail.platforms)
        }
    }

    private fun validateRailIds(assetsDir: String, strictJson: Json, detailIds: Set<Long>) {
        listOf("trending", "popular-playing", "popular-wanted", "popular-twitch", "popular-upcoming").forEach { railName ->
            val railFile = File(assetsDir, "fixtures/v1/$railName.json")
            val railIds = strictJson.decodeFromString<List<Long>>(railFile.readText())
            assertTrue("Rail $railName contains IDs not in games.json", railIds.all { it in detailIds })
        }
    }

    private fun validateWitcherDetails(details: List<GameDetailsDto>) {
        val witcher = details.first { it.id == 1942L }
        assertEquals("The Witcher 3: Wild Hunt", witcher.name)
        assertEquals("https://www.igdb.com/games/the-witcher-3-wild-hunt", witcher.url)
        assertEquals(WITCHER_TOTAL_RATING_COUNT, witcher.totalRatingCount)
        assertTrue(witcher.themes.contains("Fantasy"))
        assertTrue(witcher.companies.any { it.name == "CD Projekt RED" && it.isDeveloper })
    }

    private fun validateMediaAndEdgeCases(details: List<GameDetailsDto>) {
        assertTrue(details.all { it.coverUrl == null || it.coverUrl.startsWith("file:///android_asset/covers/") })
        assertTrue(details.all { it.screenshots.all { shot -> shot.startsWith("file:///android_asset/screenshots/") } })
        assertTrue(details.all { it.artworkUrl == null || it.artworkUrl.startsWith("file:///android_asset/screenshots/") })
        assertTrue(details.all { it.genres.isNotEmpty() && it.platforms.isNotEmpty() })
        assertTrue(details.all { it.videos.all { video -> video.videoId.isNotBlank() } })
        assertTrue(details.all { it.similarGames.all { s -> !s.name.isNullOrBlank() } })

        val retro = details.first { it.id == 900004L }
        assertTrue(retro.similarGames.isEmpty())
        assertTrue(retro.screenshots.isEmpty())
        assertTrue(retro.videos.isEmpty())
        org.junit.Assert.assertNull(retro.timeToBeatMainSeconds)
        org.junit.Assert.assertNull(retro.timeToBeatCompleteSeconds)

        val arena = details.first { it.id == 900012L }
        assertEquals(ARENA_RATING, arena.rating)
        org.junit.Assert.assertNull(arena.timeToBeatMainSeconds)
        assertEquals(ARENA_COMPLETE_SECONDS, arena.timeToBeatCompleteSeconds)

        val gtaDetails = details.first { it.id == 900003L }
        assertEquals(2, gtaDetails.releaseDates.size)
        assertTrue(gtaDetails.releaseDates.all { it.year == GTA_YEAR && it.dateEpochSeconds == null })
    }

    private companion object {
        const val WITCHER_TOTAL_RATING_COUNT = 5451L
        const val ARENA_RATING = 42.0
        const val ARENA_COMPLETE_SECONDS = 720000L
        const val GTA_YEAR = 2026
    }

    @Test
    fun `getGameDetails returns enriched game by ID or throws when not found`() = runTest {
        val game: GameDetailsDto = fakeDataSource.getGameDetails(1942L)
        assertEquals("The Witcher 3: Wild Hunt", game.name)
        assertTrue(game.screenshots.isNotEmpty())
        assertTrue(game.similarGames.isNotEmpty())

        try {
            fakeDataSource.getGameDetails(999999L)
            fail("Expected NoSuchElementException for unknown game id")
        } catch (_: NoSuchElementException) {
            // Expected
        }
    }

    @Test
    fun getRecommendationCandidates_emptySeedsAndTags_returnsEmpty() = runTest {
        val pool = fakeDataSource.getRecommendationCandidates(
            genres = emptyList(),
            themes = emptyList(),
            platforms = emptyList(),
            exclude = emptySet(),
            similarTo = emptyList(),
            limit = 10,
        )
        assertTrue(pool.isEmpty())
    }

    @Test
    fun getRecommendationCandidates_filtersExcludeDedupsAndMarksSimilar() = runTest {
        val witcher = fakeDataSource.getGameDetails(1942L)
        val similarId = witcher.similarGames.first().id
        val pool = fakeDataSource.getRecommendationCandidates(
            genres = witcher.genres.take(1),
            themes = emptyList(),
            platforms = emptyList(),
            exclude = setOf(1942L),
            similarTo = listOf(1942L),
            limit = 30,
        )
        assertTrue(pool.none { it.id == 1942L })
        val similar = pool.firstOrNull { it.id == similarId }
        if (similar != null) {
            assertTrue(1942L in similar.similarToGameIds)
        }
        assertTrue(pool.isNotEmpty())
    }

    @Test
    fun getTrendingGames_preservesFixtureOrder_notRatingDesc() = runTest {
        val trending = fakeDataSource.getTrendingGames(limit = 6, offset = 0)
        val topRated = fakeDataSource.getTopRatedGames(limit = 10, offset = 0)
        assertEquals(listOf(72L, 14593L, 1877L, 119388L, 204350L, 25076L), trending.map { it.id })
        assertTrue(trending.map { it.id } != topRated.take(trending.size).map { it.id })
    }
    @Test
    fun getPopularPage_usesRailFixtureAndReportsEnd() = runTest {
        val page = fakeDataSource.getPopularPage("playing", limit = 2, offset = 0)

        assertEquals(listOf(119133L, 1942L), page.items.map { it.id })
        assertEquals(2, page.nextOffset)
        assertTrue(!page.endReached)
    }

    @Test
    fun `searchGames filters mock games by query text`() = runTest {
        val results = fakeDataSource.searchGames(query = "Witcher", limit = 10, offset = 0)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { game ->
            game.name.contains("Witcher", ignoreCase = true) ||
                game.genres.any { g -> g.contains("Witcher", ignoreCase = true) }
        })
    }

    @Test
    fun `searchGames with filters only applies genre and rating filters and sort`() = runTest {
        val results = fakeDataSource.searchGames(
            query = null,
            genres = listOf("Role-playing (RPG)"),
            minRating = 90,
            sort = "rating_desc",
            limit = 10,
            offset = 0,
        )
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { (it.rating ?: 0.0) >= 90.0 })
        assertTrue(results.all { it.genres.contains("Role-playing (RPG)") })
        // Verify sorted by rating desc
        for (i in 0 until results.size - 1) {
            assertTrue((results[i].rating ?: 0.0) >= (results[i + 1].rating ?: 0.0))
        }
    }

    @Test
    fun `searchGames multi-genre AND keeps supersets and drops partial matches`() = runTest {
        val results = fakeDataSource.searchGames(
            query = null,
            genres = listOf("Role-playing (RPG)", "Adventure"),
            limit = 30,
            offset = 0,
        )
        assertTrue(results.isNotEmpty())
        val names = results.map { it.name }
        assertTrue(names.any { it.contains("Witcher", ignoreCase = true) })
        assertTrue(results.all { game ->
            game.genres.any { it.equals("Role-playing (RPG)", ignoreCase = true) } &&
                game.genres.any { it.equals("Adventure", ignoreCase = true) }
        })
    }

    @Test
    fun `searchGames mixed genre and theme follows BFF AND semantics`() = runTest {
        val results = fakeDataSource.searchGames(
            query = null,
            genres = listOf("Role-playing (RPG)", "Action"),
            limit = 30,
            offset = 0,
        )

        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.id == 1942L }) // The Witcher 3: RPG + Action theme
        assertTrue(results.none { it.id == 900011L }) // RPG but no Action theme
    }

    @Test
    fun `searchGames multi-platform OR includes games matching any requested platform including PC alias`() = runTest {
        val results = fakeDataSource.searchGames(
            query = null,
            platforms = listOf("PC (Microsoft Windows)", "Nintendo Switch"),
            limit = 30,
            offset = 0,
        )
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { game ->
            game.platforms.any { it.equals("PC", ignoreCase = true) || it.equals("Nintendo Switch", ignoreCase = true) }
        })
    }

    @Test
    fun `searchGames rejects blank query without any filters`() = runTest {
        try {
            fakeDataSource.searchGames(query = "   ", limit = 10, offset = 0)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }
}
