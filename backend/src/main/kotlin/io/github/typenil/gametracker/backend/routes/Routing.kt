package io.github.typenil.gametracker.backend.routes

import io.github.typenil.gametracker.backend.application.BffDependencies
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(deps: BffDependencies) {
    routing {
        healthRoutes(deps.igdbConfig, deps.cache)
        gamesRoutes(deps.igdbService, deps.cache)
    }
}
