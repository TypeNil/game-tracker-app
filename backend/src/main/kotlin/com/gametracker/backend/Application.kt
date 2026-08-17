package com.gametracker.backend

import com.gametracker.backend.application.BffDependencies
import com.gametracker.backend.application.ServerConfig
import com.gametracker.backend.error.configureErrorHandling
import com.gametracker.backend.routes.configureRouting
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

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
 * Поддерживает внедрение [customDeps] для изолированного модульного и интеграционного тестирования.
 */
fun Application.module(customDeps: BffDependencies? = null) {
    val deps = customDeps ?: BffDependencies.createProduction(environment.config).also { prodDeps ->
        monitor.subscribe(ApplicationStopped) {
            logger.info("Application stopped. Closing production dependencies...")
            prodDeps.close()
        }
    }

    install(XForwardedHeaders)

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
            }
        )
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(RateLimit) {
        register(RateLimitName("api_v1")) {
            rateLimiter(limit = 10, refillPeriod = 1.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    configureErrorHandling()
    configureRouting(deps)
}
