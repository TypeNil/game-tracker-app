package io.github.typenil.gametracker.backend.application

import io.github.typenil.gametracker.backend.auth.IgdbTokenManager
import io.github.typenil.gametracker.backend.auth.IgdbTokenManagerImpl
import io.github.typenil.gametracker.backend.cache.BffCache
import io.github.typenil.gametracker.backend.igdb.IgdbHttpClientFactory
import io.github.typenil.gametracker.backend.igdb.IgdbService
import io.github.typenil.gametracker.backend.igdb.SmoothRateLimiter
import io.ktor.client.HttpClient
import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

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
        private val logger = LoggerFactory.getLogger("BffDependencies")

        fun createProduction(
            config: ApplicationConfig,
            igdbConfig: IgdbConfig = IgdbConfigImpl(config)
        ): BffDependencies {
            if (!igdbConfig.isConfigured) {
                logger.warn(IGDB_CREDENTIALS_MISSING_MESSAGE)
            }
            require(igdbConfig.isConfigured) {
                IGDB_CREDENTIALS_MISSING_MESSAGE
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
