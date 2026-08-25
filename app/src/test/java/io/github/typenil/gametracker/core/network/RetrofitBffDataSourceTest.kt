package io.github.typenil.gametracker.core.network

import io.github.typenil.gametracker.core.network.api.BffApiService
import io.github.typenil.gametracker.core.network.datasource.RetrofitBffDataSource
import io.github.typenil.gametracker.core.network.di.NetworkModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class RetrofitBffDataSourceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var dataSource: RetrofitBffDataSource

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val json = NetworkModule.provideJson()
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        val apiService = retrofit.create(BffApiService::class.java)
        dataSource = RetrofitBffDataSource(apiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getTopRatedGames sends GET to v1_discover_top-rated and parses 200 OK response`() = runTest {
        val jsonPayload = """
            [
                {
                    "id": 101,
                    "name": "Elden Ring",
                    "coverUrl": "https://example.com/elden_ring.jpg",
                    "rating": 96.0,
                    "releaseDateEpochSeconds": 1645747200,
                    "summary": "Action RPG in Lands Between",
                    "genres": ["Action", "RPG"],
                    "platforms": ["PC", "PS5"]
                }
            ]
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonPayload)
        )

        val games = dataSource.getTopRatedGames(limit = 20, offset = 0)
        val recordedRequest = mockWebServer.takeRequest()

        assertEquals("GET", recordedRequest.method)
        assertEquals("/v1/discover/top-rated", recordedRequest.requestUrl?.encodedPath)
        assertEquals("20", recordedRequest.requestUrl?.queryParameter("limit"))
        assertEquals("0", recordedRequest.requestUrl?.queryParameter("offset"))

        assertEquals(1, games.size)
        val firstGame = games[0]
        assertEquals(101L, firstGame.id)
        assertEquals("Elden Ring", firstGame.name)
        assertEquals(96.0, firstGame.rating)
        assertEquals(listOf("Action", "RPG"), firstGame.genres)
    }

    @Test
    fun getTrendingGames_hitsDiscoverTrending() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":72,"name":"Portal 2","genres":["Puzzle"],"platforms":["PC"]}]""")
        )
        val games = dataSource.getTrendingGames(limit = 20, offset = 0)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/v1/discover/trending", recorded.requestUrl?.encodedPath)
        assertEquals("20", recorded.requestUrl?.queryParameter("limit"))
        assertEquals(72L, games.single().id)
    }
    @Test
    fun getPopularPage_sendsTypeAndOffsetAndParsesEnvelope() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"items":[{"id":72,"name":"Portal 2"}],"nextOffset":20,"endReached":false}"""),
        )

        val page = dataSource.getPopularPage("playing", 20, 0)
        val request = mockWebServer.takeRequest()
        assertEquals("/v1/discover/popular/page", request.requestUrl?.encodedPath)
        assertEquals("playing", request.requestUrl?.queryParameter("type"))
        assertEquals(72L, page.items.single().id)
        assertEquals(20, page.nextOffset)
        assertTrue(!page.endReached)
    }

    @Test
    fun `searchGames deserializes minimal JSON with omitted fields into defaults`() = runTest {
        val minimalPayload = """
            [
                {
                    "id": 42,
                    "name": "Minimal Game"
                }
            ]
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(minimalPayload)
        )

        val games = dataSource.searchGames(query = "witcher", limit = 30, offset = 0)
        val recordedRequest = mockWebServer.takeRequest()

        assertEquals("GET", recordedRequest.method)
        assertEquals("/v1/games/search", recordedRequest.requestUrl?.encodedPath)
        assertEquals("witcher", recordedRequest.requestUrl?.queryParameter("q"))
        assertEquals("30", recordedRequest.requestUrl?.queryParameter("limit"))
        assertEquals("0", recordedRequest.requestUrl?.queryParameter("offset"))

        assertEquals(1, games.size)
        val game = games[0]
        assertEquals(42L, game.id)
        assertEquals("Minimal Game", game.name)
        assertNull(game.coverUrl)
        assertNull(game.rating)
        assertNull(game.releaseDateEpochSeconds)
        assertNull(game.summary)
        assertTrue(game.genres.isEmpty())
        assertTrue(game.platforms.isEmpty())
    }

    @Test
    fun `getGameDetails sends GET with path substitution and parses enriched object`() = runTest {
        val payload = """
            {
                "id": 42,
                "name": "Cyberpunk 2077",
                "rating": 88.0,
                "totalRating": 86.5,
                "totalRatingCount": 2187,
                "url": "https://www.igdb.com/games/cyberpunk-2077",
                "themes": ["Science fiction", "Open world"],
                "gameModes": ["Single player"],
                "releaseDates": [{"platform": "PC", "dateEpochSeconds": 1607558400, "year": 2020}],
                "companies": [{"name": "CD Projekt RED", "isDeveloper": true, "isPublisher": true}],
                "screenshots": ["https://images.igdb.com/igdb/image/upload/t_720p/sc1.jpg"],
                "videos": [{"videoId": "qIcTM8WXFjk", "name": "Official E3 Trailer"}],
                "similarGames": [{"id": 1942, "name": "The Witcher 3: Wild Hunt", "totalRating": 92.7}]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(payload)
        )

        val game = dataSource.getGameDetails(id = 42L)
        val recordedRequest = mockWebServer.takeRequest()

        assertEquals("GET", recordedRequest.method)
        assertEquals("/v1/games/42", recordedRequest.requestUrl?.encodedPath)
        assertEquals(42L, game.id)
        assertEquals("Cyberpunk 2077", game.name)
        assertEquals(88.0, game.rating)
        assertEquals(86.5, game.totalRating)
        assertEquals(2187L, game.totalRatingCount)
        assertEquals("https://www.igdb.com/games/cyberpunk-2077", game.url)
        assertEquals(listOf("Science fiction", "Open world"), game.themes)
        assertEquals(listOf("Single player"), game.gameModes)
        assertEquals(1, game.releaseDates.size)
        assertEquals("PC", game.releaseDates[0].platform)
        assertEquals(1607558400L, game.releaseDates[0].dateEpochSeconds)
        assertEquals("CD Projekt RED", game.companies[0].name)
        assertTrue(game.companies[0].isDeveloper)
        assertTrue(game.companies[0].isPublisher)
        assertEquals(1, game.screenshots.size)
        assertEquals("qIcTM8WXFjk", game.videos[0].videoId)
        assertEquals(1942L, game.similarGames[0].id)
    }

    @Test
    fun `getGameDetails parses minimal JSON with omitted fields into defaults`() = runTest {
        val minimalPayload = """
            {
                "id": 7,
                "name": "Sparse Game"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(minimalPayload)
        )

        val game = dataSource.getGameDetails(id = 7L)

        assertEquals(7L, game.id)
        assertEquals("Sparse Game", game.name)
        assertNull(game.totalRating)
        assertNull(game.totalRatingCount)
        assertNull(game.url)
        assertTrue(game.themes.isEmpty())
        assertTrue(game.releaseDates.isEmpty())
        assertTrue(game.companies.isEmpty())
        assertTrue(game.screenshots.isEmpty())
        assertTrue(game.videos.isEmpty())
        assertTrue(game.similarGames.isEmpty())
    }

    @Test
    fun `searchGames direct call throws HttpException on 400 with retained error body`() = runTest {
        val errorPayload = """{"code":"INVALID_QUERY","message":"Query too short"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorPayload)
        )

        try {
            dataSource.searchGames(query = "a", limit = 30, offset = 0)
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("INVALID_QUERY") == true)
        }
    }

    @Test
    fun `searchGames direct call throws HttpException on 429 with retained error body`() = runTest {
        val rateLimitPayload = """{"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody(rateLimitPayload)
        )

        try {
            dataSource.searchGames(query = "witcher", limit = 30, offset = 0)
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(429, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("RATE_LIMIT_EXCEEDED") == true)
        }
    }

    @Test
    fun `searchGames direct call throws HttpException on 502 with HTML body`() = runTest {
        val htmlPayload = "<html><head><title>502 Bad Gateway</title></head><body>Server error</body></html>"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setHeader("Content-Type", "text/html")
                .setBody(htmlPayload)
        )

        try {
            dataSource.searchGames(query = "witcher", limit = 30, offset = 0)
            fail("Expected HttpException to be thrown")
        } catch (e: HttpException) {
            assertEquals(502, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("502 Bad Gateway") == true)
        }
    }

    @Test
    fun getRecommendationCandidates_sendsCsvQueryAndParsesThemes() = runTest {
        val jsonPayload = """
            [
                {
                    "id": 99,
                    "name": "Similar",
                    "coverUrl": "https://example.com/c.jpg",
                    "rating": 88.0,
                    "ratingCount": 12,
                    "releaseDateEpochSeconds": 1431993600,
                    "summary": "ok",
                    "genres": ["RPG"],
                    "themes": ["Fantasy"],
                    "platforms": ["PC"],
                    "similarToGameIds": [1942]
                }
            ]
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonPayload)
        )

        val pool = dataSource.getRecommendationCandidates(
            genres = listOf("RPG"),
            themes = emptyList(),
            platforms = emptyList(),
            exclude = setOf(1L),
            similarTo = listOf(1942L),
            limit = 30,
        )
        val recorded = mockWebServer.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/recommendations/candidates", recorded.requestUrl?.encodedPath)
        assertEquals("RPG", recorded.requestUrl?.queryParameter("genres"))
        assertEquals("1", recorded.requestUrl?.queryParameter("exclude"))
        assertEquals("1942", recorded.requestUrl?.queryParameter("similarTo"))
        assertEquals("30", recorded.requestUrl?.queryParameter("limit"))
        assertNull(recorded.requestUrl?.queryParameter("themes"))
        assertEquals(1, pool.size)
        assertEquals(listOf("Fantasy"), pool[0].themes)
        assertEquals(listOf(1942L), pool[0].similarToGameIds)
        assertEquals(12L, pool[0].ratingCount)
    }
}
