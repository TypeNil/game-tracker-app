package com.gametracker.backend.igdb

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.error.UpstreamBadGatewayException
import com.gametracker.backend.error.UpstreamRateLimitException
import com.gametracker.backend.error.UpstreamServiceUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class IgdbServiceTest {

    private val mockConfig = object : IgdbConfig {
        override val clientId = "test_client_id"
        override val clientSecret = "test_client_secret"
    }

    private class TestTokenManager(var token: String = "valid_token") : IgdbTokenManager {
        var invalidatedToken: String? = null
        override suspend fun getValidAccessToken(): String = token
        override fun invalidateToken(badToken: String) {
            invalidatedToken = badToken
            token = "refreshed_token"
        }
    }

    private val sampleJson = """
        [
            {
                "id": 1020,
                "name": "The Witcher 3",
                "rating": 92.5,
                "first_release_date": 1431993600,
                "cover": {
                    "id": 12,
                    "image_id": "co1wyy"
                },
                "genres": [{"id": 1, "name": "RPG"}],
                "platforms": [{"id": 6, "name": "PC"}]
            }
        ]
    """.trimIndent()

    private fun createClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Test
    fun `queryGames sends correct headers and returns parsed games`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("test_client_id", request.headers["Client-ID"])
            assertEquals("Bearer valid_token", request.headers["Authorization"])
            assertEquals("application/json", request.headers["Accept"])

            val body = String(request.body.toByteArray())
            assertTrue(body.contains("fields name"))

            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val games = service.queryGames("fields name;")

        assertEquals(1, games.size)
        assertEquals(1020L, games[0].id)
        assertEquals("The Witcher 3", games[0].name)
    }

    @Test
    fun `queryGames retries once upon 401 Unauthorized with token invalidation`() = runTest {
        var attempt = 0
        val engine = MockEngine { request ->
            attempt++
            if (attempt == 1) {
                assertEquals("Bearer valid_token", request.headers["Authorization"])
                respond(content = "Unauthorized", status = HttpStatusCode.Unauthorized)
            } else {
                assertEquals("Bearer refreshed_token", request.headers["Authorization"])
                respond(
                    content = sampleJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val tokenManager = TestTokenManager()
        val service = IgdbService(createClient(engine), tokenManager, mockConfig)
        val games = service.queryGames("fields name;")

        assertEquals("valid_token", tokenManager.invalidatedToken)
        assertEquals(2, attempt)
        assertEquals(1, games.size)
    }

    @Test
    fun `queryGames throws UpstreamBadGatewayException on repeated 401`() = runTest {
        val engine = MockEngine {
            respond(content = "Unauthorized", status = HttpStatusCode.Unauthorized)
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        val ex = result.exceptionOrNull()
        assertTrue(ex is UpstreamBadGatewayException)
    }

    @Test
    fun `queryGames throws UpstreamRateLimitException with parsed delta seconds`() = runTest {
        val engine = MockEngine {
            respond(
                content = "Rate limit reached",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "15")
            )
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        val ex = result.exceptionOrNull() as UpstreamRateLimitException
        assertEquals(15L, ex.retryAfterSeconds)
    }

    @Test
    fun `queryGames throws UpstreamRateLimitException with parsed HTTP date`() = runTest {
        val futureDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.ofInstant(Instant.now().plusSeconds(25), ZoneOffset.UTC)
        )
        val engine = MockEngine {
            respond(
                content = "Rate limit reached",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, futureDate)
            )
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        val ex = result.exceptionOrNull() as UpstreamRateLimitException
        assertTrue(ex.retryAfterSeconds in 20L..30L)
    }

    @Test
    fun `queryGames throws UpstreamBadGatewayException on malformed JSON`() = runTest {
        val engine = MockEngine {
            respond(
                content = "invalid-json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        assertTrue(result.exceptionOrNull() is UpstreamBadGatewayException)
    }

    @Test
    fun `queryGames retries on 503 and throws UpstreamServiceUnavailableException on exhaustion`() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond(content = "Service Unavailable", status = HttpStatusCode.ServiceUnavailable)
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        assertTrue(result.exceptionOrNull() is UpstreamServiceUnavailableException)
        assertEquals(3, attempts)
    }
}
