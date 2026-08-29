package com.gametracker.backend.routes

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.error.ErrorResponse
import com.gametracker.backend.models.RecommendationCandidateDto
import com.gametracker.backend.models.RecommendationCandidatesRequest
import com.gametracker.backend.error.configureErrorHandling
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.models.GameDetailsDto
import com.gametracker.backend.models.GameDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class GamesRoutesTest {

    private val mockConfig = object : IgdbConfig {
        override val clientId = "test_client_id"
        override val clientSecret = "test_client_secret"
    }

    private val mockTokenManager = object : IgdbTokenManager {
        override suspend fun getValidAccessToken() = "mock_token"
        override fun invalidateToken(badToken: String) {}
    }

    private val sampleIgdbJson = """
        [
            {
                "id": 1020,
                "name": "The Witcher 3: Wild Hunt",
                "rating": 92.5,
                "summary": "RPG masterpiece",
                "first_release_date": 1431993600,
                "cover": {
                    "id": 888,
                    "image_id": "co1wyy"
                },
                "genres": [{"id": 1, "name": "Role-playing (RPG)"}],
                "platforms": [{"id": 6, "name": "PC (Microsoft Windows)"}]
            }
        ]
    """.trimIndent()

    private fun createMockService(
        jsonResponse: String = sampleIgdbJson,
        timeToBeatJson: String = "[]",
    ): IgdbService {
        val engine = MockEngine { request ->
            val content = if (request.url.encodedPath.endsWith("/v4/game_time_to_beats")) {
                timeToBeatJson
            } else {
                jsonResponse
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return IgdbService(client, mockTokenManager, mockConfig)
    }

    private fun Application.testModule(service: IgdbService, cache: BffCache) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(RateLimit) {
            register(RateLimitName("api_v1")) {
                rateLimiter(limit = 100, refillPeriod = 1.seconds)
            }
        }
        configureErrorHandling()
        routing {
            gamesRoutes(service, cache)
        }
    }

    @Test
    fun `top-rated endpoint returns enriched GameDto list with genres and platforms`() = testApplication {
        val service = createMockService()
        val cache = BffCache()

        application {
            testModule(service, cache)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/v1/discover/top-rated?limit=10")
        assertEquals(HttpStatusCode.OK, response.status)

        val games = response.body<List<GameDto>>()
        assertEquals(1, games.size)
        val game = games[0]
        assertEquals(1020L, game.id)
        assertEquals("The Witcher 3: Wild Hunt", game.name)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg", game.coverUrl)
        assertEquals(92.5, game.rating!!, 0.01)
        assertEquals(1431993600L, game.releaseDateEpochSeconds)
        assertEquals(listOf("Role-playing (RPG)"), game.genres)
        assertEquals(listOf("PC"), game.platforms)
        cache.close()
    }

    @Test
    fun `top-rated with non-numeric limit returns 400 Bad Request`() = testApplication {
        val service = createMockService()
        val cache = BffCache()

        application {
            testModule(service, cache)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/v1/discover/top-rated?limit=abc")
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        cache.close()
    }

    @Test
    fun `search with empty or blank query returns 400 Bad Request`() = testApplication {
        val service = createMockService()
        val cache = BffCache()

        application {
            testModule(service, cache)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/v1/games/search?q=")
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        cache.close()
    }

    @Test
    fun `search with invalid characters returns 400 Bad Request`() = testApplication {
        val service = createMockService()
        val cache = BffCache()

        application {
            testModule(service, cache)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/v1/games/search?q=witcher\"")
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        cache.close()
    }

    @Test
    fun `search with valid query returns matching games`() = testApplication {
        val service = createMockService()
        val cache = BffCache()

        application {
            testModule(service, cache)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/v1/games/search?q=witcher")
        assertEquals(HttpStatusCode.OK, response.status)

        val games = response.body<List<GameDto>>()
        assertEquals(1, games.size)
        assertEquals("The Witcher 3: Wild Hunt", games[0].name)
        cache.close()
    }

    @Test
    fun `game details returns 400 Bad Request for zero, negative or non-numeric id`() = testApplication {
        val service = createMockService()
        val cache = BffCache()

        application {
            testModule(service, cache)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val responseZero = client.get("/v1/games/0")
        assertEquals(HttpStatusCode.BadRequest, responseZero.status)

        val responseNegative = client.get("/v1/games/-5")
        assertEquals(HttpStatusCode.BadRequest, responseNegative.status)

        val responseNonNumeric = client.get("/v1/games/not-a-number")
        assertEquals(HttpStatusCode.BadRequest, responseNonNumeric.status)
        cache.close()
    }

    @Test
    fun `game details returns 404 Not Found when game not found in IGDB`() = testApplication {
        val service = createMockService(jsonResponse = "[]")
        val cache = BffCache()

        application {
            testModule(service, cache)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/v1/games/999999")
        assertEquals(HttpStatusCode.NotFound, response.status)

        val error = response.body<ErrorResponse>()
        assertEquals("NOT_FOUND", error.code)
        cache.close()
    }

    /**
     * Полный details-ответ, повторяющий структуру реального IGDB-ответа для
     * id 1942 (live-верифицировано 2026-08-22). Списки сверх лимитов, записи
     * без ключевых полей и дубли (platform, year) проверяют трим/скип/дедуп.
     */
    private fun detailsIgdbJson(): String {
        val screenshots = (1..9).joinToString(",") { """{"id": $it, "image_id": "sc$it"}""" }
        val videos = (1..7).joinToString(",") {
            """{"id": $it, "name": "Trailer $it", "video_id": "vid$it"}"""
        }
        val similarGames = (1..11).joinToString(",") {
            val ratingField = if (it == 2) """"rating": 85.0""" else """"total_rating": ${80.0 + it}"""
            val extra = if (it == 1) {
                """, "genres": [{"id": 5, "name": "Shooter"}], """ +
                    """"platforms": [{"id": 6, "name": "PC (Microsoft Windows)", "abbreviation": "PC"}]"""
            } else {
                ""
            }
            """{"id": ${2000 + it}, "name": "Similar $it", """ +
                """"cover": {"id": $it, "image_id": "co$it"}, $ratingField$extra}"""
        } + """, {"name": "Broken Similar", "cover": {"id": 99, "image_id": "co99"}}"""
        return """
            [
                {
                    "id": 1020,
                    "name": "The Witcher 3: Wild Hunt",
                    "rating": 92.5,
                    "total_rating": 92.7,
                    "total_rating_count": 5451,
                    "url": "https://www.igdb.com/games/the-witcher-3-wild-hunt",
                    "summary": "RPG masterpiece",
                    "first_release_date": 1431993600,
                    "cover": {
                        "id": 888,
                        "image_id": "co1wyy",
                        "url": "//images.igdb.com/igdb/image/upload/t_thumb/co1wyy.jpg"
                    },
                    "genres": [{"id": 1, "name": "Role-playing (RPG)"}],
                    "platforms": [{"id": 6, "name": "PC (Microsoft Windows)", "abbreviation": "PC"}],
                    "themes": [{"id": 17, "name": "Fantasy"}, {"id": 38, "name": "Open world"}, {"id": 99}],
                    "game_modes": [{"id": 1, "name": "Single player"}, {"id": 2, "name": "Multiplayer"}],
                    "release_dates": [
                        {"id": 1, "date": 1431993600, "y": 2015, "platform": {"id": 6, "name": "PC (Microsoft Windows)", "abbreviation": "PC"}},
                        {"id": 2, "y": 2015, "platform": {"id": 6, "name": "PC (Microsoft Windows)", "abbreviation": "PC"}},
                        {"id": 3, "date": 1611792000, "y": 2021, "platform": {"id": 130, "name": "Nintendo Switch", "abbreviation": "Switch"}}
                    ],
                    "involved_companies": [
                        {"id": 1, "company": {"id": 908, "name": "CD Projekt RED"}, "developer": true, "publisher": false},
                        {"id": 2, "company": {"id": 50, "name": "WB Games"}, "developer": false, "publisher": true},
                        {"id": 3, "company": {"id": 5696, "name": "D3T Limited"}, "developer": false, "publisher": false},
                        {"id": 4, "company": {"id": 777}}
                    ],
                    "screenshots": [$screenshots],
                    "artworks": [{"id": 2}, {"id": 1, "image_id": "art1"}],
                    "videos": [$videos],
                    "similar_games": [$similarGames]
                }
            ]
        """.trimIndent()
    }

    @Test
    fun `game details returns enriched GameDetailsDto with trims, dedup and skipped incomplete rows`() =
        testApplication {
            val service = createMockService(
                jsonResponse = detailsIgdbJson(),
                timeToBeatJson = """[{"id":7,"hastily":183600,"completely":622800}]""",
            )
            val cache = BffCache()

            application {
                testModule(service, cache)
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/v1/games/1020")
            assertEquals(HttpStatusCode.OK, response.status)

            val game = response.body<GameDetailsDto>()
            assertEquals(1020L, game.id)
            assertEquals("The Witcher 3: Wild Hunt", game.name)
            assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg", game.coverUrl)
            assertEquals(92.5, game.rating!!, 0.01)
            assertEquals(92.7, game.totalRating!!, 0.01)
            assertEquals(5451L, game.totalRatingCount)
            assertEquals("https://www.igdb.com/games/the-witcher-3-wild-hunt", game.url)

            // Неполные строки (theme без name, company без name) пропускаются
            assertEquals(listOf("Fantasy", "Open world"), game.themes)
            assertEquals(listOf("Single player", "Multiplayer"), game.gameModes)
            assertEquals(3, game.companies.size)
            assertEquals("CD Projekt RED", game.companies[0].name)
            assertEquals(true, game.companies[0].isDeveloper)
            assertEquals(false, game.companies[0].isPublisher)
            assertEquals(false, game.companies[1].isDeveloper)
            assertEquals(true, game.companies[1].isPublisher)
            assertEquals(false, game.companies[2].isDeveloper)
            assertEquals(false, game.companies[2].isPublisher)

            // Дедуп по (platform, year): запись с точной датой выигрывает у бездатой
            assertEquals(2, game.releaseDates.size)
            assertEquals("PC", game.releaseDates[0].platform)
            assertEquals(1431993600L, game.releaseDates[0].dateEpochSeconds)
            // PC (Microsoft Windows) + abbreviation PC → canonical "PC"

            assertEquals("Nintendo Switch", game.releaseDates[1].platform)
            assertEquals(1611792000L, game.releaseDates[1].dateEpochSeconds)

            // Серверные тримы: 9 -> 8 скриншотов, 7 -> 5 видео, 11 -> 10 похожих
            assertEquals(8, game.screenshots.size)
            assertEquals("https://images.igdb.com/igdb/image/upload/t_720p/sc1.jpg", game.screenshots[0])
            assertEquals(5, game.videos.size)
            assertEquals("vid1", game.videos[0].videoId)
            assertEquals("Trailer 1", game.videos[0].name)
            assertEquals(10, game.similarGames.size)
            // Similar без id (невозможна навигация) пропускается вместе с тримом
            assertTrue(game.similarGames.none { it.name == "Broken Similar" })
            assertEquals(2001L, game.similarGames[0].id)
            assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co1.jpg", game.similarGames[0].coverUrl)
            assertEquals(listOf("Shooter"), game.similarGames[0].genres)
            assertEquals(listOf("PC"), game.similarGames[0].platforms)
            // Fallback totalRating ?: rating
            assertEquals(85.0, game.similarGames[1].totalRating!!, 0.01)
            // Artwork без image_id пропускается; времена прохождения приходят отдельным endpoint.
            assertEquals("https://images.igdb.com/igdb/image/upload/t_720p/art1.jpg", game.artworkUrl)
            assertEquals(183600L, game.timeToBeatMainSeconds)
            assertEquals(622800L, game.timeToBeatCompleteSeconds)
            cache.close()
        }

    /**
     * Реальная форма sparse-ответа IGDB (live id 87388 PapiHop): отсутствующие
     * сущности IGDB опускает целиком — ни cover, ни списков, ни null-литералов.
     */
    @Test
    fun `game details tolerates sparse IGDB payload without cover and nested collections`() =
        testApplication {
            val service = createMockService(
                jsonResponse = """[{"id": 87388, "name": "PapiHop", "rating": 90.0}]"""
            )
            val cache = BffCache()

            application {
                testModule(service, cache)
            }

            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }

            val response = client.get("/v1/games/87388")
            assertEquals(HttpStatusCode.OK, response.status)

            val game = response.body<GameDetailsDto>()
            assertEquals(87388L, game.id)
            assertEquals("PapiHop", game.name)
            assertNull(game.coverUrl)
            assertEquals(90.0, game.rating!!, 0.01)
            assertNull(game.totalRating)
            assertNull(game.totalRatingCount)
            assertEquals(emptyList<String>(), game.themes)
            assertTrue(game.releaseDates.isEmpty())
            assertTrue(game.companies.isEmpty())
            assertEquals(emptyList<String>(), game.screenshots)
            assertTrue(game.videos.isEmpty())
            assertTrue(game.similarGames.isEmpty())
            assertNull(game.timeToBeatMainSeconds)
            assertNull(game.timeToBeatCompleteSeconds)
            cache.close()
        }

    private fun createInspectingService(
        handler: (String) -> String,
    ): Pair<IgdbService, MutableList<String>> {
        val seen = mutableListOf<String>()
        val engine = MockEngine { request ->
            val text = (request.body as TextContent).text
            seen += text
            respond(
                content = handler(text),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return IgdbService(client, mockTokenManager, mockConfig) to seen
    }

    private fun candidateJson(id: Long, name: String = "Game $id") = """
        {
            "id": $id,
            "name": "$name",
            "rating": 88.0,
            "rating_count": 12,
            "summary": "ok",
            "first_release_date": 1431993600,
            "cover": {"id": 1, "image_id": "co1"},
            "genres": [{"id": 1, "name": "Role-playing (RPG)"}],
            "themes": [{"id": 1, "name": "Fantasy"}],
            "platforms": [{"id": 6, "name": "PC"}]
        }
    """.trimIndent()

    @Test
    fun `candidates similarTo hydrates similar games and marks seeds`() = testApplication {
        val (service, seen) = createInspectingService { body ->
            if (body.contains("similar_games.id") && !body.contains("genres.name")) {
                """[{"id":10,"name":"Seed","similar_games":[{"id":99}]}]"""
            } else {
                "[${candidateJson(99, "Similar")}]"
            }
        }
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/recommendations/candidates?similarTo=10")
        assertEquals(HttpStatusCode.OK, response.status)
        val games = response.body<List<RecommendationCandidateDto>>()
        assertEquals(1, games.size)
        assertEquals(99L, games[0].id)
        assertEquals(listOf(10L), games[0].similarToGameIds)
        assertTrue(seen[0].contains("similar_games.id"))
        assertTrue(seen[1].contains("id = (99)"))
        cache.close()
    }

    @Test
    fun `candidates tag query returns themes and ratingCount`() = testApplication {
        val (service, seen) = createInspectingService { "[${candidateJson(7)}]" }
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/recommendations/candidates?genres=RPG")
        assertEquals(HttpStatusCode.OK, response.status)
        val games = response.body<List<RecommendationCandidateDto>>()
        assertEquals(1, games.size)
        assertEquals(listOf("Fantasy"), games[0].themes)
        assertEquals(12L, games[0].ratingCount)
        assertTrue(seen.single().contains("genres.name = (\"RPG\")"))
        cache.close()
    }

    @Test
    fun `candidates exclude removes hydrated id`() = testApplication {
        val (service, _) = createInspectingService { body ->
            if (body.contains("similar_games.id") && !body.contains("genres.name")) {
                """[{"id":10,"name":"Seed","similar_games":[{"id":99}]}]"""
            } else {
                "[${candidateJson(99)}]"
            }
        }
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/recommendations/candidates?similarTo=10&exclude=99")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<List<RecommendationCandidateDto>>().isEmpty())
        cache.close()
    }

    @Test
    fun `candidates empty request returns empty without upstream`() = testApplication {
        val (service, seen) = createInspectingService { error("upstream should not be called") }
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/recommendations/candidates")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<List<RecommendationCandidateDto>>().isEmpty())
        assertTrue(seen.isEmpty())
        cache.close()
    }

    @Test
    fun `candidates reject quoted genre`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/recommendations/candidates?genres=RP%22G")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("BAD_REQUEST", response.body<ErrorResponse>().code)
        cache.close()
    }

    @Test
    fun `candidates cache second identical request`() = testApplication {
        val (service, seen) = createInspectingService { "[${candidateJson(7)}]" }
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        repeat(2) {
            val response = client.get("/v1/recommendations/candidates?genres=RPG")
            assertEquals(HttpStatusCode.OK, response.status)
        }
        assertEquals(1, seen.size)
        cache.close()
    }

    @Test
    fun `trending endpoint hydrates primitives in popularity order`() = testApplication {
        val seenPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            seenPaths += request.url.encodedPath
            val body = (request.body as TextContent).text
            val json = if (request.url.encodedPath.contains("popularity_primitives")) {
                assertTrue(body.contains("popularity_type = 1"))
                """[{"game_id":72,"value":0.9},{"game_id":14593,"value":0.5}]"""
            } else {
                """[
                    {"id":14593,"name":"Hollow Knight","rating":91.8,
                     "cover":{"id":1,"image_id":"cohk"},
                     "genres":[{"id":1,"name":"Adventure"}],
                     "platforms":[{"id":6,"name":"PC"}]},
                    {"id":72,"name":"Portal 2","rating":94.6,
                     "cover":{"id":2,"image_id":"cop2"},
                     "genres":[{"id":1,"name":"Puzzle"}],
                     "platforms":[{"id":6,"name":"PC"}]}
                ]"""
            }
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = IgdbService(http, mockTokenManager, mockConfig)
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/discover/trending?limit=10")
        assertEquals(HttpStatusCode.OK, response.status)
        val games = response.body<List<GameDto>>()
        assertEquals(listOf(72L, 14593L), games.map { it.id })
        assertTrue(seenPaths[0].contains("popularity_primitives"))
        assertTrue(seenPaths[1].contains("/v4/games") || seenPaths[1].endsWith("/games"))
        cache.close()
    }
    @Test
    fun `recommendation candidates request accepts page parameters`() {
        val request = RecommendationCandidatesRequest(
            genresParam = "RPG",
            limitParam = 10,
            offsetParam = 20,
            sortParam = "follows",
        )

        assertEquals(10, request.limit)
        assertEquals(20, request.offset)
        assertTrue(request.toTagApicalypseQuery().contains("limit 30;"))
        assertTrue(request.toTagApicalypseQuery().contains("offset 0;"))
    }
}
