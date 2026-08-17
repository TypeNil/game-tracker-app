package com.gametracker.backend.routes

import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.cache.CachePolicy
import com.gametracker.backend.error.ErrorResponse
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.models.GameDetailsRequest
import com.gametracker.backend.models.SearchRequest
import com.gametracker.backend.models.TopRatedRequest
import com.gametracker.backend.models.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

fun Route.gamesRoutes(igdbService: IgdbService, cache: BffCache) {
    val logger = LoggerFactory.getLogger("GamesRoutes")

    route("/v1") {
        rateLimit(RateLimitName("api_v1")) {
            get("/discover/top-rated") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                val request = TopRatedRequest(limit, offset)

                logger.info("Fetching top rated games with cacheKey: {}", request.cacheKey)
                val games = cache.getOrPut(request.cacheKey, CachePolicy.POPULAR) {
                    igdbService.queryGames(request.toApicalypseQuery()).map { it.toDto() }
                }
                call.respond(games)
            }

            get("/games/search") {
                val q = call.request.queryParameters["q"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                // Валидация выполняется через канонический SearchRequest (выбрасывает IllegalArgumentException)
                val request = SearchRequest(q, limit, offset)

                logger.info("Searching games with cacheKey: {}", request.cacheKey)
                val games = cache.getOrPut(request.cacheKey, CachePolicy.SEARCH) {
                    igdbService.queryGames(request.toApicalypseQuery()).map { it.toDto() }
                }
                call.respond(games)
            }

            get("/games/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                val request = GameDetailsRequest(id)

                val games = cache.getOrPut(request.cacheKey, CachePolicy.GAME_DETAILS) {
                    igdbService.queryGames(request.toApicalypseQuery()).map { it.toDto() }
                }

                val game = games.firstOrNull()
                if (game == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Game not found"))
                } else {
                    call.respond(game)
                }
            }
        }
    }
}
