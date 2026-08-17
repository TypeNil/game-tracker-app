package com.gametracker.backend.routes

import com.gametracker.backend.cache.BffCache
import com.gametracker.backend.igdb.IgdbService
import com.gametracker.backend.models.toDto
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

fun Route.gamesRoutes(igdbService: IgdbService, cache: BffCache) {
    val logger = LoggerFactory.getLogger("GamesRoutes")

    route("/api/games") {
        
        get("/popular") {
            logger.info("Fetching popular games")
            val games = cache.getOrPut("popular_games") {
                val query = """
                    fields name, rating, cover.url, cover.image_id, summary, first_release_date;
                    where rating != null & cover != null & first_release_date != null & rating > 80;
                    sort rating desc;
                    limit 20;
                """.trimIndent()
                
                igdbService.queryGames(query).map { it.toDto() }
            }
            
            call.respond(games)
        }

        get("/search") {
            val q = call.request.queryParameters["q"]
            if (q.isNullOrBlank()) {
                call.respond(emptyList<String>())
                return@get
            }
            
            logger.info("Searching games with query: {}", q)
            val cacheKey = "search_$q"
            val games = cache.getOrPut(cacheKey) {
                val query = """
                    search "$q";
                    fields name, rating, cover.url, cover.image_id, summary, first_release_date;
                    where cover != null;
                    limit 20;
                """.trimIndent()
                
                igdbService.queryGames(query).map { it.toDto() }
            }
            
            call.respond(games)
        }
    }
}
