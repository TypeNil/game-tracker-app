package com.gametracker.backend

import com.gametracker.backend.application.BffDependencies
import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.routes.HealthStatusDto
import com.gametracker.backend.routes.PingResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApplicationTest {

    private fun createTestDeps(): BffDependencies {
        val client = HttpClient(MockEngine { respondOk() })
        val config = object : IgdbConfig {
            override val clientId = "test_id"
            override val clientSecret = "test_secret"
        }
        val tokenManager = object : IgdbTokenManager {
            override suspend fun getValidAccessToken() = "test_token"
            override fun invalidateToken(badToken: String) {}
        }
        val service = IgdbService(client, tokenManager, config)
        return BffDependencies(
            igdbConfig = config,
            httpClient = client,
            tokenManager = tokenManager,
            igdbService = service,
            cache = BffCache(),
            ownsHttpClient = false
        )
    }

    @Test
    fun healthEndpointReturnsStatusUp() = testApplication {
        val deps = createTestDeps()
        application {
            module(deps)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<HealthStatusDto>()
        assertEquals("UP", body.status)
        assertEquals("GameTracker-BFF", body.service)
        assertEquals("1.0.0", body.version)
        assertNotNull(body.timestamp)
    }

    @Test
    fun pingEndpointReturnsPong() = testApplication {
        val deps = createTestDeps()
        application {
            module(deps)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/api/v1/ping")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<PingResponseDto>()
        assertEquals("pong", body.message)
    }
}
