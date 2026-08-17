package com.gametracker.backend

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.application.ServerConfig
import com.gametracker.backend.auth.IgdbTokenManagerImpl
import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.error.configureErrorHandling
import com.gametracker.backend.igdb.IgdbHttpClientFactory
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.routes.configureRouting
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val logger = LoggerFactory.getLogger("Application")

fun main() {
    val config = ServerConfig()
    logger.info("Starting GameTracker BFF on http://${config.host}:${config.port} ...")

    embeddedServer(
        factory = Netty,
        port = config.port,
        host = config.host,
        module = Application::module
    ).start(wait = true)
}

/**
 * Основной модуль конфигурации Ktor.
 * Настраивает плагины ContentNegotiation, CallLogging, StatusPages и корневую маршрутизацию.
 */
fun Application.module() {
    val igdbConfig = IgdbConfig(environment.config)
    val httpClient = IgdbHttpClientFactory.create()
    val tokenManager = IgdbTokenManagerImpl(igdbConfig, httpClient)
    val cache = BffCache()
    val igdbService = IgdbService(httpClient, tokenManager, igdbConfig)

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            }
        )
    }

    install(CallLogging) {
        level = Level.INFO
    }

    configureErrorHandling()
    configureRouting(igdbService, cache)
}
