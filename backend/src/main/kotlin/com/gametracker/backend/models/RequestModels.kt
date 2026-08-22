package com.gametracker.backend.models

import java.text.Normalizer

private val SAFE_PUNCTUATION = setOf('-', '_', ':', '\'', '!', '?', '.', ',', '&', '+')
private const val QUERY_FIELDS =
    "fields name, rating, cover.url, cover.image_id, first_release_date, summary, genres.name, platforms.name;\n"

/**
 * Поля для деталей игры. Списковые запросы остаются на [QUERY_FIELDS] — список должен
 * оставаться тощим, а details-ответ не должен менять URL-размер обложек списков.
 * Каждый ref запрашивается с полной цепочкой expansion (например,
 * similar_games.cover.image_id): неexpanded ref IGDB возвращает как int, и декод
 * вложенного объекта упадёт (маскируясь под 502).
 */
private const val DETAILS_FIELDS =
    "fields name, rating, total_rating, total_rating_count, url, summary, cover.url, cover.image_id, " +
        "first_release_date, genres.name, themes.name, game_modes.name, platforms.name, platforms.abbreviation, " +
        "release_dates.date, release_dates.y, release_dates.platform.abbreviation, " +
        "involved_companies.company.name, involved_companies.developer, involved_companies.publisher, " +
        "screenshots.image_id, videos.video_id, videos.name, " +
        "similar_games.id, similar_games.name, similar_games.cover.image_id, similar_games.total_rating;\n"

/**
 * Валидатор и канонический нормализатор поисковой строки.
 */
object SearchQueryValidator {
    const val MIN_LENGTH = 1
    const val MAX_LENGTH = 100

    fun validateAndNormalize(rawQuery: String?): String {
        if (rawQuery.isNullOrBlank()) {
            throw IllegalArgumentException("Search query 'q' parameter cannot be blank")
        }

        val trimmed = rawQuery.trim()
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
        val codePointCount = normalized.codePointCount(0, normalized.length)

        if (codePointCount < MIN_LENGTH || codePointCount > MAX_LENGTH) {
            throw IllegalArgumentException("Search query must be between $MIN_LENGTH and $MAX_LENGTH characters")
        }

        var i = 0
        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)

            // Запрет управляющих символов, кавычек и обратных слэшей
            if (Character.isISOControl(cp) || cp == '"'.code || cp == '\\'.code) {
                throw IllegalArgumentException("Search query contains illegal control characters or quotes")
            }

            // Строгий Unicode allowlist
            val isAllowed = Character.isLetterOrDigit(cp) ||
                Character.isSpaceChar(cp) ||
                (cp < 65536 && cp.toChar() in SAFE_PUNCTUATION)

            if (!isAllowed) {
                val symbol = String(Character.toChars(cp))
                throw IllegalArgumentException("Search query contains unpermitted character '$symbol'")
            }

            i += Character.charCount(cp)
        }

        return normalized.lowercase()
    }
}

/**
 * Каноническая модель запроса поиска игр.
 */
class SearchRequest(
    rawQuery: String?,
    limitParam: Int? = null,
    offsetParam: Int? = null
) {
    val canonicalQuery: String = SearchQueryValidator.validateAndNormalize(rawQuery)
    val limit: Int = (limitParam ?: 20).coerceIn(1, 30)
    val offset: Int = (offsetParam ?: 0).coerceIn(0, 1000)

    val cacheKey: String = "search_${canonicalQuery}_${limit}_${offset}"

    fun toApicalypseQuery(): String {
        val builder = StringBuilder(QUERY_FIELDS)
        builder.append("search \"$canonicalQuery\";\n")
        builder.append("where cover != null;\n")
        builder.append("limit $limit;\n")
        builder.append("offset $offset;")
        return builder.toString()
    }
}

/**
 * Каноническая модель запроса популярных игр с наивысшим рейтингом.
 */
class TopRatedRequest(
    limitParam: Int? = null,
    offsetParam: Int? = null
) {
    val limit: Int = (limitParam ?: 20).coerceIn(1, 30)
    val offset: Int = (offsetParam ?: 0).coerceIn(0, 1000)

    val cacheKey: String = "top_rated_${limit}_${offset}"

    fun toApicalypseQuery(): String {
        val builder = StringBuilder(QUERY_FIELDS)
        builder.append("where rating >= 80 & cover != null;\n")
        builder.append("sort rating desc;\n")
        builder.append("limit $limit;\n")
        builder.append("offset $offset;")
        return builder.toString()
    }
}

/**
 * Каноническая модель запроса детальной информации об игре.
 */
class GameDetailsRequest(rawId: Long?) {
    val id: Long

    init {
        if (rawId == null || rawId <= 0) {
            throw IllegalArgumentException("Game ID must be a positive integer")
        }
        id = rawId
    }

    /**
     * Версионированный ключ: BffCache хранит значения как Any, поэтому старые
     * «тощие» записи кэша не должны десериализоваться под новый тип до истечения TTL.
     */
    val cacheKey: String = "game_v2_$id"

    fun toApicalypseQuery(): String {
        // Без фильтра cover != null: переход в details по похожей игре без обложки
        // не должен отдавать 404 (списки по-прежнему фильтруют coverless-игры).
        return "${DETAILS_FIELDS}where id = ($id);\nlimit 1;"
    }
}
