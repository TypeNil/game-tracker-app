package com.gametracker.backend.routes

import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.igdb.IgdbService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/**
 * Конфигурация всех маршрутов приложения Ktor.
 */
fun Application.configureRouting(igdbService: IgdbService, cache: BffCache) {
    routing {
        healthRoutes()
        gamesRoutes(igdbService, cache)
    }
}
