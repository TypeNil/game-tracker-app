package io.github.typenil.gametracker.backend

import io.github.typenil.gametracker.backend.application.BffDependencies
import io.github.typenil.gametracker.backend.application.ServerConfig
import io.github.typenil.gametracker.backend.error.configureErrorHandling
import io.github.typenil.gametracker.backend.routes.configureRouting
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
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
import io.ktor.server.response.header
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("Application")

val defaultTrustedProxyHosts: Set<String> = setOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost")

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
 * Main Ktor module configuration.
 * Supports injecting [customDeps] for isolated unit and integration testing.
 */
fun Application.module(customDeps: BffDependencies? = null) {
    val deps = customDeps ?: try {
        BffDependencies.createProduction(environment.config)
    } catch (e: IllegalArgumentException) {
        logger.warn("Failed to initialize production dependencies: {}", e.message)
        throw e
    }

    // Unconditional resource cleanup registration on application stop
    monitor.subscribe(ApplicationStopped) {
        logger.info("Application stopped. Closing dependencies...")
        deps.close()
    }

    val trustedProxiesCount = environment.config.propertyOrNull("bff.proxy.trustedProxiesCount")
        ?.getString()?.toIntOrNull() ?: 0

    val configuredTrustedHosts = environment.config.propertyOrNull("bff.proxy.trustedHosts")
        ?.getString()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        ?: defaultTrustedProxyHosts

    if (trustedProxiesCount > 0) {
        install(XForwardedHeaders) {
            skipLastProxies(trustedProxiesCount)
        }
    }

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
            requestKey { call -> resolveClientIp(call, trustedProxiesCount > 0, configuredTrustedHosts) }
            modifyResponse { call, _ ->
                call.response.header(HttpHeaders.RetryAfter, "1")
            }
        }
    }

    configureErrorHandling()
    configureRouting(deps)
}

/**
 * Resolves the real client IP with anti-spoofing protection.
 *
 * Proxy security policy:
 * - If [isProxyEnabled] == false (direct deployment), X-Forwarded-* headers are ignored
 *   and [ApplicationCall.request.local.remoteHost] is returned.
 * - If [isProxyEnabled] == true (deployment behind a reverse proxy):
 *   the direct transport peer ([ApplicationCall.request.local.remoteHost]) is checked.
 *   Only if it exactly matches a value in [trustedHosts] (e.g. "127.0.0.1", "10.0.0.2")
 *   is the address from the [ApplicationCall.request.origin.remoteHost] header used.
 *   If the direct peer is not trusted, its own IP is returned, so untrusted headers cannot forge it.
 */
fun resolveClientIp(
    call: ApplicationCall,
    isProxyEnabled: Boolean,
    trustedHosts: Set<String> = defaultTrustedProxyHosts
): String {
    if (!isProxyEnabled) {
        return call.request.local.remoteHost
    }

    val directPeer = call.request.local.remoteHost
    return if (directPeer in trustedHosts) {
        call.request.origin.remoteHost
    } else {
        // The direct client may be spoofing X-Forwarded-For: use its real IP
        directPeer
    }
}
