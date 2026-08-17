package com.gametracker.backend.igdb

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Фабрика HTTP-клиента для взаимодействия с внешними сервисами (Twitch OAuth2 и IGDB v4 API).
 * Использует легковесный асинхронный корутиновый движок CIO.
 */
object IgdbHttpClientFactory {
    private val clientLogger = LoggerFactory.getLogger("IgdbHttpClient")

    fun create(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                }
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    clientLogger.debug(message)
                }
            }
            level = LogLevel.INFO
            sanitizeHeader { header -> header == io.ktor.http.HttpHeaders.Authorization }
        }
    }
}
