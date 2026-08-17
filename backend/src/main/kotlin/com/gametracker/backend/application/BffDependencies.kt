package com.gametracker.backend.application

import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.auth.IgdbTokenManagerImpl
import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.igdb.IgdbHttpClientFactory
import com.gametracker.backend.igdb.IgdbService
import io.ktor.client.HttpClient
import io.ktor.server.config.ApplicationConfig
import java.time.Clock

/**
 * Контейнер зависимостей BFF с явным управлением жизненным циклом ресурсов.
 */
class BffDependencies(
    val igdbConfig: IgdbConfig,
    val httpClient: HttpClient,
    val tokenManager: IgdbTokenManager,
    val igdbService: IgdbService,
    val cache: BffCache,
    val clock: Clock = Clock.systemUTC(),
    val ownsHttpClient: Boolean = true
) : AutoCloseable {

    override fun close() {
        if (ownsHttpClient) {
            httpClient.close()
        }
    }

    companion object {
        fun createProduction(config: ApplicationConfig): BffDependencies {
            val igdbConfig = IgdbConfigImpl(config)
            require(igdbConfig.isConfigured) {
                "FATAL: IGDB credentials (clientId/clientSecret) are not configured. Cannot start BFF in production mode."
            }

            val httpClient = IgdbHttpClientFactory.create()
            val tokenManager = IgdbTokenManagerImpl(igdbConfig, httpClient)
            val igdbService = IgdbService(httpClient, tokenManager, igdbConfig)
            val cache = BffCache()

            return BffDependencies(
                igdbConfig = igdbConfig,
                httpClient = httpClient,
                tokenManager = tokenManager,
                igdbService = igdbService,
                cache = cache,
                ownsHttpClient = true
            )
        }
    }
}
