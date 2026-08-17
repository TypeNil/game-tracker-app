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
 * Фабрика HTTP-клиента для взаимодействия с Twitch OAuth2 и IGDB v4 API.
 * Настраивает таймауты, сериализацию и логирование без дублирующих внешних плагинов повторов.
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
