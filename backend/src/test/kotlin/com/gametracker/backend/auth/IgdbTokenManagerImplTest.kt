package com.gametracker.backend.auth

import com.gametracker.backend.application.IgdbConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

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

    @Test
    fun `getValidAccessToken fetches new token on first call`() = runTest {
        val engine = MockEngine { request ->
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
        
        // Assert exactly 1 request was made
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `getValidAccessToken uses cached token on subsequent calls`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"access_token":"token_123","expires_in":3600,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        val token1 = manager.getValidAccessToken()
        val token2 = manager.getValidAccessToken()
        val token3 = manager.getValidAccessToken()

        assertEquals("token_123", token1)
        assertEquals("token_123", token2)
        assertEquals("token_123", token3)
        
        // Assert only 1 request was made despite 3 calls
        assertEquals(1, requestCount)
    }

    @Test
    fun `concurrent calls only trigger one network request`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(
                content = """{"access_token":"token_123","expires_in":3600,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createMockClient(engine)
        val manager = IgdbTokenManagerImpl(createMockConfig(), client)

        // Launch 100 concurrent requests for the token
        val deferreds = (1..100).map {
            async { manager.getValidAccessToken() }
        }
        
        val tokens = deferreds.awaitAll()
        
        // All 100 callers should get the same token
        tokens.forEach { assertEquals("token_123", it) }
        
        // The network request should only happen exactly once thanks to Mutex
        assertEquals(1, requestCount)
    }

    @Test
    fun `forceRefresh clears cache and triggers new fetch`() = runTest {
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
        
        manager.forceRefresh()
        
        val token2 = manager.getValidAccessToken()
        assertEquals("token_2", token2)
        
        assertEquals(2, requestCount)
    }
}
