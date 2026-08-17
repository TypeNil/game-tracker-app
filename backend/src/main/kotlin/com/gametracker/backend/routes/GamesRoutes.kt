package com.gametracker.backend.routes

import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.cache.CachePolicy
import com.gametracker.backend.error.ErrorResponse
import com.gametracker.backend.igdb.IgdbQueryBuilder
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.models.GameDto
import com.gametracker.backend.models.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

fun Route.gamesRoutes(igdbService: IgdbService, cache: BffCache) {
    val logger = LoggerFactory.getLogger("GamesRoutes")

    route("/v1") {
        
        get("/discover/top-rated") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            
            logger.info("Fetching top rated games, limit=$limit, offset=$offset")
            val cacheKey = "top_rated_${limit}_${offset}"
            val games = cache.getOrPut(cacheKey, CachePolicy.POPULAR) {
                val query = IgdbQueryBuilder.build(
                    minRating = 80,
                    sortBy = "rating",
                    sortDirection = "desc",
                    limit = limit,
                    offset = offset
                )
                igdbService.queryGames(query).map { it.toDto() }
            }
            call.respond(games)
        }

        get("/games/search") {
            val q = call.request.queryParameters["q"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            
            if (q.isNullOrBlank()) {
                call.respond(emptyList<GameDto>())
                return@get
            }
            
            logger.info("Searching games with query: {}, limit={}, offset={}", q, limit, offset)
            val cacheKey = "search_${q}_${limit}_${offset}"
            val games = cache.getOrPut(cacheKey, CachePolicy.SEARCH) {
                val query = IgdbQueryBuilder.build(
                    searchQuery = q,
                    limit = limit,
                    offset = offset
                )
                igdbService.queryGames(query).map { it.toDto() }
            }
            call.respond(games)
        }
        
        get("/games/{id}") {
            val idStr = call.parameters["id"]
            val id = idStr?.toLongOrNull()
            
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid game ID"))
                return@get
            }
            
            val cacheKey = "game_$id"
            val games = cache.getOrPut(cacheKey, CachePolicy.GAME_DETAILS) {
                val query = IgdbQueryBuilder.build(ids = listOf(id))
                igdbService.queryGames(query).map { it.toDto() }
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
