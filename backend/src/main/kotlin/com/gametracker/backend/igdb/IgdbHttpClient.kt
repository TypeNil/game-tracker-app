package com.gametracker.backend.igdb

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val TIMEOUT_MILLIS = 10_000L

/**
 * HTTP client factory for Twitch OAuth2 and the IGDB v4 API.
 * Configures timeouts, serialization and logging without duplicate external retry plugins.
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
            requestTimeoutMillis = TIMEOUT_MILLIS
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = TIMEOUT_MILLIS
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    clientLogger.debug(message)
                }
            }
            level = LogLevel.INFO
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
    }
}
