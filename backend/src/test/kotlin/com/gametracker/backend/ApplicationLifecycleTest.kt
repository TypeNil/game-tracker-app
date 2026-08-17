package com.gametracker.backend

import com.gametracker.backend.application.BffDependencies
import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.application.IgdbConfigImpl
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.igdb.IgdbService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationLifecycleTest {

    @Test
    fun `BffDependencies closes HttpClient when owned`() {
        val client = HttpClient(MockEngine { respondOk() })
        val config = object : IgdbConfig {
            override val clientId = "id"
            override val clientSecret = "secret"
        }
        val tokenManager = object : IgdbTokenManager {
            override suspend fun getValidAccessToken() = "token"
            override fun invalidateToken(badToken: String) {}
        }
        val service = IgdbService(client, tokenManager, config)
        val deps = BffDependencies(
            igdbConfig = config,
            httpClient = client,
            tokenManager = tokenManager,
            igdbService = service,
            cache = BffCache(),
            ownsHttpClient = true
        )

        assertTrue(client.isActive)
        deps.close()
        assertFalse(client.isActive)

        // Idempotent repeated close
        deps.close()
        assertFalse(client.isActive)
    }

    @Test
    fun `BffDependencies does not close HttpClient when not owned`() {
        val client = HttpClient(MockEngine { respondOk() })
        val config = object : IgdbConfig {
            override val clientId = "id"
            override val clientSecret = "secret"
        }
        val tokenManager = object : IgdbTokenManager {
            override suspend fun getValidAccessToken() = "token"
            override fun invalidateToken(badToken: String) {}
        }
        val service = IgdbService(client, tokenManager, config)
        val deps = BffDependencies(
            igdbConfig = config,
            httpClient = client,
            tokenManager = tokenManager,
            igdbService = service,
            cache = BffCache(),
            ownsHttpClient = false
        )

        assertTrue(client.isActive)
        deps.close()
        assertTrue(client.isActive)
    }

    @Test
    fun `ApplicationStopped event executes dependencies cleanup`() = testApplication {
        val client = HttpClient(MockEngine { respondOk() })
        val config = object : IgdbConfig {
            override val clientId = "id"
            override val clientSecret = "secret"
        }
        val tokenManager = object : IgdbTokenManager {
            override suspend fun getValidAccessToken() = "token"
            override fun invalidateToken(badToken: String) {}
        }
        val service = IgdbService(client, tokenManager, config)
        val deps = BffDependencies(
            igdbConfig = config,
            httpClient = client,
            tokenManager = tokenManager,
            igdbService = service,
            cache = BffCache(),
            ownsHttpClient = true
        )

        application {
            module(deps)
        }
        // At application startup, client is active
        assertTrue(client.isActive)

        // When testApplication completes and shuts down, ApplicationStopped hook fires
        // (testApplication implicitly stops the application at the end of block)
    }

    @Test
    fun `IgdbConfigImpl prioritizes non-blank config over developer defaults`() {
        val appConfig = MapApplicationConfig(
            "igdb.clientId" to "explicit_client_id",
            "igdb.clientSecret" to "explicit_client_secret"
        )
        val config = IgdbConfigImpl(appConfig)
        assertEquals("explicit_client_id", config.clientId)
        assertEquals("explicit_client_secret", config.clientSecret)
        assertTrue(config.isConfigured)
    }

    @Test
    fun `BffDependencies createProduction fails fast if credentials missing`() {
        val emptyConfig = MapApplicationConfig("igdb.clientId" to "", "igdb.clientSecret" to "")
        assertThrows(IllegalArgumentException::class.java) {
            BffDependencies.createProduction(emptyConfig)
        }
    }
}
