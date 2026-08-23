package com.gametracker.backend.models

import java.text.Normalizer

private val SAFE_PUNCTUATION = setOf('-', '_', ':', '\'', '!', '?', '.', ',', '&', '+')
private const val QUERY_FIELDS =
    "fields name, rating, cover.url, cover.image_id, first_release_date, summary, genres.name, platforms.name;\n"

private const val CANDIDATE_FIELDS =
    "fields name, rating, rating_count, cover.url, cover.image_id, first_release_date, summary, " +
        "genres.name, themes.name, platforms.name;\n"

private val TAG_PUNCTUATION = setOf(
    '-', '_', ':', '\'', '!', '?', '.', '&', '+',
    '(', ')', '[', ']', '/', '|',
)

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
        "release_dates.date, release_dates.y, release_dates.platform.name, release_dates.platform.abbreviation, " +
        "involved_companies.company.name, involved_companies.developer, involved_companies.publisher, " +
        "screenshots.image_id, videos.video_id, videos.name, " +
        "similar_games.id, similar_games.name, similar_games.cover.image_id, similar_games.total_rating, similar_games.rating;\n"

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
 * Trending games from IGDB PopScore visits (popularity_type = 1).
 */
class TrendingRequest(
    limitParam: Int? = null,
    offsetParam: Int? = null,
) {
    val limit: Int = (limitParam ?: 20).coerceIn(1, 30)
    val offset: Int = (offsetParam ?: 0).coerceIn(0, 1000)
    val cacheKey: String = "trending_${limit}_${offset}"

    fun toPrimitivesApicalypseQuery(): String {
        val fetch = (limit + offset).coerceAtMost(MAX_PRIMITIVE_FETCH)
        return "fields game_id,value;\nsort value desc;\nlimit $fetch;\nwhere popularity_type = $VISITS_TYPE;"
    }

    fun toHydrateApicalypseQuery(ids: List<Long>): String {
        require(ids.isNotEmpty())
        return "${QUERY_FIELDS}where id = (${ids.joinToString(",")}) & cover != null;\nlimit ${ids.size};"
    }

    companion object {
        const val VISITS_TYPE = 1
        const val MAX_PRIMITIVE_FETCH = 50
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

/**
 * IGDB genre/theme/platform names as stored on the wire. Not lowercased.
 */
object TagNameValidator {
    const val MIN_LENGTH = 1
    const val MAX_LENGTH = 60

    fun validate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Tag name cannot be blank")
        }
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
        val codePointCount = normalized.codePointCount(0, normalized.length)
        if (codePointCount < MIN_LENGTH || codePointCount > MAX_LENGTH) {
            throw IllegalArgumentException("Tag name must be between $MIN_LENGTH and $MAX_LENGTH characters")
        }
        var i = 0
        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)
            if (Character.isISOControl(cp) || cp == '"'.code || cp == '\\'.code) {
                throw IllegalArgumentException("Tag name contains illegal control characters or quotes")
            }
            val isAllowed = Character.isLetterOrDigit(cp) ||
                Character.isSpaceChar(cp) ||
                (cp < 65536 && cp.toChar() in TAG_PUNCTUATION)
            if (!isAllowed) {
                val symbol = String(Character.toChars(cp))
                throw IllegalArgumentException("Tag name contains unpermitted character '$symbol'")
            }
            i += Character.charCount(cp)
        }
        return normalized
    }
}

/**
 * Canonical request for GET /v1/recommendations/candidates.
 */
class RecommendationCandidatesRequest(
    genresParam: String? = null,
    themesParam: String? = null,
    platformsParam: String? = null,
    excludeParam: String? = null,
    similarToParam: String? = null,
    limitParam: Int? = null,
) {
    val genres: List<String> = parseTags(genresParam, "genres")
    val themes: List<String> = parseTags(themesParam, "themes")
    val platforms: List<String> = parseTags(platformsParam, "platforms")
    val exclude: List<Long> = parseIds(excludeParam, "exclude", max = MAX_EXCLUDE)
    val similarTo: List<Long> = parseIds(similarToParam, "similarTo", max = MAX_SIMILAR_TO)
    val limit: Int = (limitParam ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
    val blockedIds: List<Long> = (exclude + similarTo).distinct()

    val cacheKey: String = buildString {
        append("rec_")
        append(genres.sorted().joinToString(","))
        append('|')
        append(themes.sorted().joinToString(","))
        append('|')
        append(platforms.sorted().joinToString(","))
        append('|')
        append(exclude.sorted().joinToString(","))
        append('|')
        append(similarTo.sorted().joinToString(","))
        append('|')
        append(limit)
    }

    val hasTags: Boolean = genres.isNotEmpty() || themes.isNotEmpty() || platforms.isNotEmpty()

    fun toTagApicalypseQuery(): String {
        val where = buildList {
            add("cover != null")
            add("rating >= 70")
            tagOrGroup()?.let { add(it) }
            idExclusion()?.let { add(it) }
        }.joinToString(" & ")
        return "${CANDIDATE_FIELDS}where $where;\nsort rating desc;\nlimit $limit;"
    }

    fun toSimilarSeedsApicalypseQuery(): String {
        require(similarTo.isNotEmpty()) { "similarTo must not be empty" }
        return "fields id, name, similar_games.id;\nwhere id = (${similarTo.joinToString(",")});\nlimit ${similarTo.size};"
    }

    fun toHydrateApicalypseQuery(ids: List<Long>): String {
        require(ids.isNotEmpty()) { "hydrate ids must not be empty" }
        val where = buildList {
            add("id = (${ids.joinToString(",")})")
            add("cover != null")
            idExclusion()?.let { add(it) }
        }.joinToString(" & ")
        return "${CANDIDATE_FIELDS}where $where;\nlimit ${ids.size.coerceAtMost(MAX_LIMIT)};"
    }

    private fun tagOrGroup(): String? {
        val clauses = buildList {
            if (genres.isNotEmpty()) add("genres.name = ${quotedList(genres)}")
            if (themes.isNotEmpty()) add("themes.name = ${quotedList(themes)}")
            if (platforms.isNotEmpty()) add("platforms.name = ${quotedList(platforms)}")
        }
        if (clauses.isEmpty()) return null
        if (clauses.size == 1) return clauses[0]
        return clauses.joinToString(" | ", prefix = "(", postfix = ")")
    }

    private fun idExclusion(): String? =
        blockedIds.takeIf { it.isNotEmpty() }?.let { "id != (${it.joinToString(",")})" }

    companion object {
        const val DEFAULT_LIMIT = 30
        const val MAX_LIMIT = 30
        const val MAX_TAGS = 5
        const val MAX_EXCLUDE = 50
        const val MAX_SIMILAR_TO = 10

        private fun parseTags(raw: String?, label: String): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val tags = raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map(TagNameValidator::validate)
                .distinct()
            if (tags.size > MAX_TAGS) {
                throw IllegalArgumentException("Query parameter '$label' accepts at most $MAX_TAGS values")
            }
            return tags
        }

        private fun parseIds(raw: String?, label: String, max: Int): List<Long> {
            if (raw.isNullOrBlank()) return emptyList()
            val ids = raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { token ->
                    val id = token.toLongOrNull()
                        ?: throw IllegalArgumentException("Query parameter '$label' must be a comma-separated list of integers")
                    if (id <= 0L) {
                        throw IllegalArgumentException("Query parameter '$label' must contain positive integers")
                    }
                    id
                }
                .distinct()
            if (ids.size > max) {
                throw IllegalArgumentException("Query parameter '$label' accepts at most $max values")
            }
            return ids
        }

        private fun quotedList(values: List<String>): String =
            values.joinToString(prefix = "(", postfix = ")") { "\"$it\"" }
    }
}
