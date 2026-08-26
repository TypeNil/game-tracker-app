package com.gametracker.backend.routes

import com.gametracker.backend.application.BffDependencies
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/**
 * Конфигурация всех маршрутов приложения Ktor.
 */
fun Application.configureRouting(deps: BffDependencies) {
    routing {
        healthRoutes(deps.igdbConfig, deps.cache)
        gamesRoutes(deps.igdbService, deps.cache)
    }
}
