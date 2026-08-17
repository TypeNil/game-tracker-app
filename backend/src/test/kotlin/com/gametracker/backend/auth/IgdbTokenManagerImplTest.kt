package com.gametracker.backend.auth

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.error.UpstreamBadGatewayException
import com.gametracker.backend.error.UpstreamRateLimitException
import com.gametracker.backend.error.UpstreamServiceUnavailableException
import com.gametracker.backend.error.UpstreamTimeoutException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class IgdbTokenManagerImplTest {

    private fun createMockConfig() = object : IgdbConfig {
        override val clientId = "test_client_id"
        override val clientSecret = "test_client_secret"
    }

    private fun createMockClient(mockEngine: MockEngine) = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private class MutableClock(private var currentInstant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = currentInstant
        fun advanceSeconds(seconds: Long) {
            currentInstant = currentInstant.plusSeconds(seconds)
        }
    }

    @Test
    fun `getValidAccessToken sends credentials in POST body and not in URL query`() = runTest {
        val engine = MockEngine { request ->
            assertFalse(request.url.toString().contains("client_secret"))
            val bodyString = String(request.body.toByteArray())
            assertTrue(bodyString.contains("client_id=test_client_id"))
            assertTrue(bodyString.contains("client_secret=test_client_secret"))
            assertTrue(bodyString.contains("grant_type=client_credentials"))

            respond(
                content = """{"access_token":"token_123","expires_in":3600,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val token = manager.getValidAccessToken()
        assertEquals("token_123", token)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `getValidAccessToken respects Clock expiration and 60s buffer`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"access_token":"token_$requestCount","expires_in":3600,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val clock = MutableClock(Instant.parse("2026-08-18T00:00:00Z"))
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client, clock)

        val token1 = manager.getValidAccessToken()
        assertEquals("token_1", token1)
        assertEquals(1, requestCount)

        clock.advanceSeconds(3500)
        val token2 = manager.getValidAccessToken()
        assertEquals("token_1", token2)
        assertEquals(1, requestCount)

        clock.advanceSeconds(50)
        val token3 = manager.getValidAccessToken()
        assertEquals("token_2", token3)
        assertEquals(2, requestCount)
    }

    @Test
    fun `concurrent calls trigger only one network request`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"access_token":"token_concurrent","expires_in":3600,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val deferreds = (1..50).map {
            async { manager.getValidAccessToken() }
        }
        val tokens = deferreds.awaitAll()

        tokens.forEach { assertEquals("token_concurrent", it) }
        assertEquals(1, requestCount)
    }

    @Test
    fun `invalidateToken with matching token clears cache`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"access_token":"token_$requestCount","expires_in":3600,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val token1 = manager.getValidAccessToken()
        assertEquals("token_1", token1)

        manager.invalidateToken("token_1")

        val token2 = manager.getValidAccessToken()
        assertEquals("token_2", token2)
        assertEquals(2, requestCount)
    }

    @Test
    fun `invalidateToken with stale token does not clear newly refreshed token (CAS protection)`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"access_token":"token_$requestCount","expires_in":3600,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val token1 = manager.getValidAccessToken()
        manager.invalidateToken(token1)

        val token2 = manager.getValidAccessToken()
        assertEquals("token_2", token2)

        manager.invalidateToken("token_1")

        val token3 = manager.getValidAccessToken()
        assertEquals("token_2", token3)
        assertEquals(2, requestCount)
    }

    @Test
    fun `Twitch OAuth error 400 throws UpstreamBadGatewayException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"status":400,"message":"invalid client"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val result = runCatching { manager.getValidAccessToken() }
        assertTrue(result.exceptionOrNull() is UpstreamBadGatewayException)
    }

    @Test
    fun `Twitch OAuth error 429 throws UpstreamRateLimitException with parsed Retry-After`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"status":429,"message":"too many requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "25")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val result = runCatching { manager.getValidAccessToken() }
        val ex = result.exceptionOrNull()
        assertTrue(ex is UpstreamRateLimitException)
        assertEquals(25L, (ex as UpstreamRateLimitException).retryAfterSeconds)
    }

    @Test
    fun `Twitch OAuth error 503 throws UpstreamServiceUnavailableException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"status":503,"message":"Service Unavailable"}""",
                status = HttpStatusCode.ServiceUnavailable,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val result = runCatching { manager.getValidAccessToken() }
        assertTrue(result.exceptionOrNull() is UpstreamServiceUnavailableException)
    }

    @Test
    fun `Twitch OAuth error 504 throws UpstreamTimeoutException`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"status":504,"message":"Gateway Timeout"}""",
                status = HttpStatusCode.GatewayTimeout,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val result = runCatching { manager.getValidAccessToken() }
        assertTrue(result.exceptionOrNull() is UpstreamTimeoutException)
    }

    @Test
    fun `Twitch OAuth network timeout throws UpstreamTimeoutException`() = runTest {
        val engine = MockEngine {
            throw HttpRequestTimeoutException("https://id.twitch.tv", 5000L)
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val result = runCatching { manager.getValidAccessToken() }
        assertTrue(result.exceptionOrNull() is UpstreamTimeoutException)
    }

    @Test
    fun `Twitch OAuth IOException throws UpstreamServiceUnavailableException`() = runTest {
        val engine = MockEngine {
            throw IOException("Connection refused")
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val result = runCatching { manager.getValidAccessToken() }
        assertTrue(result.exceptionOrNull() is UpstreamServiceUnavailableException)
    }

    @Test
    fun `Twitch OAuth token acquisition propagates CancellationException`() = runTest {
        val engine = MockEngine {
            throw CancellationException("Caller coroutine cancelled")
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val result = runCatching { manager.getValidAccessToken() }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
