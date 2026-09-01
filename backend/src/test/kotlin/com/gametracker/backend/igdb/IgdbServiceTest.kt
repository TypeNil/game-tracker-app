package com.gametracker.backend.igdb

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.error.UpstreamBadGatewayException
import com.gametracker.backend.error.UpstreamRateLimitException
import com.gametracker.backend.error.UpstreamServiceUnavailableException
import io.ktor.client.HttpClient
import org.slf4j.LoggerFactory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
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
        val invalidatedTokens = mutableListOf<String>()
        override suspend fun getValidAccessToken(): String = token
        override fun invalidateToken(badToken: String) {
            invalidatedTokens.add(badToken)
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
    fun `queryPopularityPrimitives posts to popularity_primitives`() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/v4/popularity_primitives"))
            respond(
                content = """[{"game_id":72,"value":0.9,"popularity_type":1}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val rows = service.queryPopularityPrimitives("fields game_id,value;")
        assertEquals(1, rows.size)
        assertEquals(72L, rows[0].gameId)
    }

    @Test
    fun `queryGameTimeToBeats posts filtered query and parses seconds`() = runTest {
        var queryBody = ""
        val engine = MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/v4/game_time_to_beats"))
            queryBody = String(request.body.toByteArray())
            respond(
                content = """[{"id":7,"hastily":183600,"completely":622800}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val rows = service.queryGameTimeToBeats(1020L)

        assertTrue(queryBody.contains("where game_id = 1020"))
        assertTrue(queryBody.contains("hastily"))
        assertTrue(queryBody.contains("completely"))
        assertEquals(1, rows.size)
        assertEquals(183600L, rows[0].hastily)
        assertEquals(622800L, rows[0].completely)
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

        assertEquals(listOf("valid_token"), tokenManager.invalidatedTokens)
        assertEquals(2, attempt)
        assertEquals(1, games.size)
    }

    @Test
    fun `queryGames invalidates second token upon repeated 401 and sanitizes error message`() = runTest {
        val engine = MockEngine {
            respond(content = "Sensitive Internal Upstream Error", status = HttpStatusCode.Unauthorized)
        }

        val tokenManager = TestTokenManager()
        val service = IgdbService(createClient(engine), tokenManager, mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        val ex = result.exceptionOrNull()

        assertTrue(ex is UpstreamBadGatewayException)
        assertFalse(ex!!.message!!.contains("Sensitive Internal Upstream Error"))
        // Check that both first and refreshed tokens were invalidated
        assertEquals(2, tokenManager.invalidatedTokens.size)
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
    fun `queryGames throws UpstreamRateLimitException with parsed HTTP date using injected Clock`() = runTest {
        val fixedInstant = Instant.parse("2026-08-18T00:00:00Z")
        val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
        val futureDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.ofInstant(fixedInstant.plusSeconds(30), ZoneOffset.UTC)
        )

        val engine = MockEngine {
            respond(
                content = "Rate limit reached",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, futureDate)
            )
        }

        val service = IgdbService(
            httpClient = createClient(engine),
            tokenManager = TestTokenManager(),
            config = mockConfig,
            clock = fixedClock
        )
        val result = runCatching { service.queryGames("fields name;") }
        val ex = result.exceptionOrNull() as UpstreamRateLimitException
        assertEquals(30L, ex.retryAfterSeconds)
    }

    @Test
    fun `queryGames throws UpstreamBadGatewayException on malformed JSON without leaking body in message`() = runTest {
        val engine = MockEngine {
            respond(
                content = "upstream-unparseable-data",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        val ex = result.exceptionOrNull()
        assertTrue(ex is UpstreamBadGatewayException)
        assertFalse(ex!!.message!!.contains("upstream-unparseable-data"))
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

    @Test
    fun `queryGames does not log non-success upstream body`() = runTest {
        val sensitiveBody = "sensitive-internal-upstream-error-details"
        val logger = LoggerFactory.getLogger("IgdbService") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            val engine = MockEngine {
                respond(
                    content = sensitiveBody,
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            }
            val service = IgdbService(
                createClient(engine),
                TestTokenManager(),
                mockConfig,
            )

            runCatching { service.queryGames("fields name;") }

            assertTrue(
                appender.list.none {
                    it.formattedMessage.contains(sensitiveBody)
                }
            )
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `queryGames on non-success status does not expose upstream body in exception message`() = runTest {
        val sensitiveBody = "sensitive-internal-upstream-error-details"
        val engine = MockEngine {
            respond(
                content = sensitiveBody,
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val service = IgdbService(createClient(engine), TestTokenManager(), mockConfig)
        val result = runCatching { service.queryGames("fields name;") }
        val ex = result.exceptionOrNull()
        assertTrue(ex is UpstreamBadGatewayException)
        assertFalse(ex!!.message!!.contains(sensitiveBody))
    }
}
