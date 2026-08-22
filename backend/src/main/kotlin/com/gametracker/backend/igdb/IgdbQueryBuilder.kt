package com.gametracker.backend.igdb

import com.gametracker.backend.models.GameDetailsRequest
import com.gametracker.backend.models.SearchRequest
import com.gametracker.backend.models.TopRatedRequest

/**
 * Утилитный построитель Apicalypse-запросов для IGDB API.
 * Делегирует каноническим моделям запросов для соблюдения согласованности.
 * [DEFAULT_FIELDS] описывает только списковые запросы; details-запрос
 * использует собственный набор полей в [com.gametracker.backend.models.GameDetailsRequest].
 */
object IgdbQueryBuilder {
    const val DEFAULT_FIELDS = "name, rating, cover.url, cover.image_id, first_release_date, summary, genres.name, platforms.name"

    fun buildSearch(query: String, limit: Int = 20, offset: Int = 0): String {
        return SearchRequest(query, limit, offset).toApicalypseQuery()
    }

    fun buildTopRated(limit: Int = 20, offset: Int = 0): String {
        return TopRatedRequest(limit, offset).toApicalypseQuery()
    }

    fun buildGameDetails(id: Long): String {
        return GameDetailsRequest(id).toApicalypseQuery()
    }
}
