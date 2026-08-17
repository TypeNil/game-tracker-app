package com.gametracker.backend.routes

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
data class PingResponseDto(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Эндпоинты проверки жизнеспособности (Liveness/Readiness probes) сервера.
 */
fun Route.healthRoutes() {
    get("/health") {
        call.respond(
            HealthStatusDto(
                status = "UP",
                service = "GameTracker-BFF",
                version = "1.0.0"
            )
        )
    }

    route("/api/v1") {
        get("/ping") {
            call.respond(
                PingResponseDto(
                    message = "pong"
                )
            )
        }
    }
}
