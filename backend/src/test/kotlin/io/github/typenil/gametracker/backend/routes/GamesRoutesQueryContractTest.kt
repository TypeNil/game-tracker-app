package io.github.typenil.gametracker.backend.routes

import io.github.typenil.gametracker.backend.application.IgdbConfig
import io.github.typenil.gametracker.backend.auth.IgdbTokenManager
import io.github.typenil.gametracker.backend.cache.BffCache
import io.github.typenil.gametracker.backend.error.ErrorResponse
import io.github.typenil.gametracker.backend.error.configureErrorHandling
import io.github.typenil.gametracker.backend.igdb.IgdbService
import io.github.typenil.gametracker.backend.models.GameDto
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamesRoutesQueryContractTest {

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
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
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
    fun `search endpoint rejects unknown query parameter naming offending key`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/games/search?query=zelda&bogus=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertTrue(error.message.contains("'bogus'"))
        cache.close()
    }

    @Test
    fun `search endpoint rejects single unknown key naming offending key`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/games/search?q=zelda&bogus=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("Unsupported query parameter: 'bogus'", error.message)
        cache.close()
    }

    @Test
    fun `search endpoint rejects multiple unknown keys with formatted list`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/games/search?zeta=1&alpha=2")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("Unsupported query parameters: 'alpha', 'zeta'", error.message)
        cache.close()
    }

    @Test
    fun `top-rated endpoint rejects unknown query parameter naming offending key`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/discover/top-rated?bogus=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("Unsupported query parameter: 'bogus'", error.message)
        cache.close()
    }

    @Test
    fun `trending endpoint rejects unknown query parameter naming offending key`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/discover/trending?bogus=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("Unsupported query parameter: 'bogus'", error.message)
        cache.close()
    }

    @Test
    fun `popular page endpoint rejects unknown query parameter naming offending key`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/discover/popular/page?type=visits&bogus=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("Unsupported query parameter: 'bogus'", error.message)
        cache.close()
    }

    @Test
    fun `game details endpoint rejects any query parameter naming offending key`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/games/1020?bogus=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("Unsupported query parameter: 'bogus'", error.message)
        cache.close()
    }

    @Test
    fun `recommendations candidates endpoint rejects unknown query parameter naming offending key`() =
        testApplication {
            val service = createMockService()
            val cache = BffCache()
            application { testModule(service, cache) }
            val client = createClient {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val response = client.get("/v1/recommendations/candidates?genres=RPG&bogus=1")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = response.body<ErrorResponse>()
            assertEquals("BAD_REQUEST", error.code)
            assertEquals("Unsupported query parameter: 'bogus'", error.message)
            cache.close()
        }

    @Test
    fun `recommendations candidates endpoint rejects offset parameter not in its contract`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.get("/v1/recommendations/candidates?genres=RPG&offset=10")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("Unsupported query parameter: 'offset'", error.message)
        cache.close()
    }

    @Test
    fun `recommendations candidates page endpoint rejects unknown query parameter naming offending key`() =
        testApplication {
            val service = createMockService()
            val cache = BffCache()
            application { testModule(service, cache) }
            val client = createClient {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val response = client.get("/v1/recommendations/candidates/page?genres=RPG&bogus=1")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = response.body<ErrorResponse>()
            assertEquals("BAD_REQUEST", error.code)
            assertEquals("Unsupported query parameter: 'bogus'", error.message)
            cache.close()
        }

    @Test
    fun `search endpoint accepts full set of documented known query parameters and returns 200`() = testApplication {
        val service = createMockService()
        val cache = BffCache()
        application { testModule(service, cache) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val path = "/v1/games/search?" +
            "q=witcher" +
            "&genres=Role-playing+(RPG)" +
            "&platforms=PC+(Microsoft+Windows)" +
            "&minRating=70" +
            "&minYear=2010" +
            "&maxYear=2024" +
            "&sort=rating_desc" +
            "&limit=10" +
            "&offset=0"

        val response = client.get(path)
        assertEquals(HttpStatusCode.OK, response.status)
        val games = response.body<List<GameDto>>()
        assertEquals(1, games.size)
        assertEquals("The Witcher 3: Wild Hunt", games[0].name)
        cache.close()
    }
}
