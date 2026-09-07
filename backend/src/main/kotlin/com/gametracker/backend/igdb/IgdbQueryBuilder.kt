package com.gametracker.backend.igdb

import com.gametracker.backend.models.GameDetailsRequest
import com.gametracker.backend.models.SearchRequest
import com.gametracker.backend.models.TopRatedRequest

/**
 * Apicalypse query builder utility for the IGDB API.
 * Delegates to the canonical request models for consistency.
 * [DEFAULT_FIELDS] describes list queries only; the details query
 * uses its own field set in [com.gametracker.backend.models.GameDetailsRequest].
 */
object IgdbQueryBuilder {
    const val DEFAULT_FIELDS = "name, rating, cover.url, cover.image_id, first_release_date, summary, genres.name, platforms.name"

    fun buildSearch(query: String, limit: Int = 20, offset: Int = 0): String {
        return SearchRequest(rawQuery = query, limitParam = limit, offsetParam = offset).toApicalypseQuery()
    }

    fun buildTopRated(limit: Int = 20, offset: Int = 0): String {
        return TopRatedRequest(limit, offset).toApicalypseQuery()
    }

    fun buildGameDetails(id: Long): String {
        return GameDetailsRequest(id).toApicalypseQuery()
    }
}
