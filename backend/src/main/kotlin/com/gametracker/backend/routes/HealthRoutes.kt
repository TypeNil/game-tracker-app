package com.gametracker.backend.routes

import com.gametracker.backend.application.IgdbConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatusDto(
    val status: String,
    val service: String,
    val version: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ReadyStatusDto(
    val status: String,
    val configured: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PingResponseDto(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Эндпоинты проверки жизнеспособности (Liveness) и готовности (Readiness).
 */
fun Route.healthRoutes(config: IgdbConfig) {
    // Liveness probe: сервис запущен и отвечает
    get("/health") {
        call.respond(
            HealthStatusDto(
                status = "UP",
                service = "GameTracker-BFF",
                version = "1.0.0"
            )
        )
    }

    get("/health/live") {
        call.respond(
            HealthStatusDto(
                status = "UP",
                service = "GameTracker-BFF",
                version = "1.0.0"
            )
        )
    }

    // Readiness probe: конфигурация валидна и сервис готов обслуживать клиентов
    get("/health/ready") {
        if (config.isConfigured) {
            call.respond(
                HttpStatusCode.OK,
                ReadyStatusDto(status = "READY", configured = true)
            )
        } else {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ReadyStatusDto(status = "NOT_READY", configured = false)
            )
        }
    }

    route("/api/v1") {
        get("/ping") {
            call.respond(
                PingResponseDto(message = "pong")
            )
        }
    }
}
