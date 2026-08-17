package com.gametracker.backend.application

import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.auth.IgdbTokenManagerImpl
import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.igdb.IgdbHttpClientFactory
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.igdb.SmoothRateLimiter
import io.ktor.client.HttpClient
import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Контейнер внедрения зависимостей BFF с явным управлением жизненным циклом ресурсов.
 */
class BffDependencies(
    val igdbConfig: IgdbConfig,
    val httpClient: HttpClient,
    val tokenManager: IgdbTokenManager,
    val igdbService: IgdbService,
    val cache: BffCache,
    val ownsHttpClient: Boolean
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger("BffDependencies")
    private val isClosed = AtomicBoolean(false)

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            logger.info("Disposing BffDependencies (ownsHttpClient={})...", ownsHttpClient)
            cache.close()
            if (ownsHttpClient) {
                httpClient.close()
            }
        }
    }

    companion object {
        fun createProduction(config: ApplicationConfig): BffDependencies {
            val igdbConfig = IgdbConfigImpl(config)
            require(igdbConfig.isConfigured) {
                "IGDB credentials are missing! Please provide IGDB_CLIENT_ID and IGDB_CLIENT_SECRET."
            }

            val client = IgdbHttpClientFactory.create()
            val tokenManager = IgdbTokenManagerImpl(igdbConfig, client)
            val rateLimiter = SmoothRateLimiter()
            val igdbService = IgdbService(client, tokenManager, igdbConfig, rateLimiter)
            val cache = BffCache()

            return BffDependencies(
                igdbConfig = igdbConfig,
                httpClient = client,
                tokenManager = tokenManager,
                igdbService = igdbService,
                cache = cache,
                ownsHttpClient = true
            )
        }
    }
}
