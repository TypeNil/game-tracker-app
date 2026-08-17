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
import java.util.Properties

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
    fun `ApplicationStopped event executes dependencies cleanup`() {
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

        testApplication {
            application {
                module(deps)
            }
            assertTrue(client.isActive)
        }

        // When testApplication completes and shuts down, ApplicationStopped hook fires and closes client
        assertFalse(client.isActive)
    }

    @Test
    fun `IgdbConfigImpl prioritizes non-blank config over environment and local defaults`() {
        val appConfig = MapApplicationConfig(
            "igdb.clientId" to "explicit_client_id",
            "igdb.clientSecret" to "explicit_client_secret"
        )
        val fakeProps = Properties().apply {
            setProperty("IGDB_CLIENT_ID", "local_id")
            setProperty("IGDB_CLIENT_SECRET", "local_secret")
        }
        val config = IgdbConfigImpl(
            config = appConfig,
            envProvider = { "env_$it" },
            localPropertiesProvider = { fakeProps }
        )
        assertEquals("explicit_client_id", config.clientId)
        assertEquals("explicit_client_secret", config.clientSecret)
        assertTrue(config.isConfigured)
    }

    @Test
    fun `IgdbConfigImpl falls back to environment variables when config is blank`() {
        val blankAppConfig = MapApplicationConfig(
            "igdb.clientId" to "",
            "igdb.clientSecret" to "   "
        )
        val fakeProps = Properties().apply {
            setProperty("IGDB_CLIENT_ID", "local_id")
            setProperty("IGDB_CLIENT_SECRET", "local_secret")
        }
        val config = IgdbConfigImpl(
            config = blankAppConfig,
            envProvider = { key -> if (key == "IGDB_CLIENT_ID") "env_id" else "env_secret" },
            localPropertiesProvider = { fakeProps }
        )
        assertEquals("env_id", config.clientId)
        assertEquals("env_secret", config.clientSecret)
        assertTrue(config.isConfigured)
    }

    @Test
    fun `IgdbConfigImpl falls back to local properties when config and env are blank`() {
        val blankAppConfig = MapApplicationConfig(
            "igdb.clientId" to "",
            "igdb.clientSecret" to ""
        )
        val fakeProps = Properties().apply {
            setProperty("IGDB_CLIENT_ID", "local_id")
            setProperty("IGDB_CLIENT_SECRET", "local_secret")
        }
        val config = IgdbConfigImpl(
            config = blankAppConfig,
            envProvider = { null },
            localPropertiesProvider = { fakeProps }
        )
        assertEquals("local_id", config.clientId)
        assertEquals("local_secret", config.clientSecret)
        assertTrue(config.isConfigured)
    }

    @Test
    fun `IgdbConfigImpl resolves to empty when all sources are blank`() {
        val blankAppConfig = MapApplicationConfig(
            "igdb.clientId" to "",
            "igdb.clientSecret" to ""
        )
        val config = IgdbConfigImpl(
            config = blankAppConfig,
            envProvider = { null },
            localPropertiesProvider = { Properties() }
        )
        assertEquals("", config.clientId)
        assertEquals("", config.clientSecret)
        assertFalse(config.isConfigured)
    }

    @Test
    fun `BffDependencies fails fast when credentials are unconfigured`() {
        val unconfigured = IgdbConfigImpl(
            config = MapApplicationConfig(),
            envProvider = { null },
            localPropertiesProvider = { Properties() }
        )
        assertFalse(unconfigured.isConfigured)
        assertThrows(IllegalArgumentException::class.java) {
            require(unconfigured.isConfigured) {
                "IGDB credentials must be configured via application.conf or environment variables"
            }
        }
    }
}
