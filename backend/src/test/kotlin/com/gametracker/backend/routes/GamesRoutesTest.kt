package com.gametracker.backend.routes

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.error.ErrorResponse
import com.gametracker.backend.error.configureErrorHandling
import com.gametracker.backend.igdb.IgdbService
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

    private fun createMockService(jsonResponse: String = sampleIgdbJson): IgdbService {
        val engine = MockEngine {
            respond(
                content = jsonResponse,
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
        assertEquals(listOf("PC (Microsoft Windows)"), game.platforms)
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
    }

    @Test
    fun `game details returns 400 Bad Request for zero or negative id`() = testApplication {
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
    }
}
