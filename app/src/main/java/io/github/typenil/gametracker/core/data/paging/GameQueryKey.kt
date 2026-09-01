package io.github.typenil.gametracker.core.data.paging

import java.text.Normalizer
import java.util.Locale

/**
 * Single source of truth (SSOT) for query keys, TTL thresholds, and canonical key serialization.
 */
sealed interface GameQueryKey {
    val key: String

    data object DiscoverTopRated : GameQueryKey {
        override val key: String = KEY_DISCOVER_TOP_RATED
    }

    data object DiscoverTrending : GameQueryKey {
        override val key: String = KEY_DISCOVER_TRENDING
    }


    data class Search(
        val query: String = "",
        val genres: List<String> = emptyList(),
        val platforms: List<String> = emptyList(),
        val minRating: Int? = null,
        val minYear: Int? = null,
        val maxYear: Int? = null,
        val sort: String? = null,
    ) : GameQueryKey {
        override val key: String = buildString {
            append("search:v2")
            append("|q=").append(encodePart(normalize(query)))
            append("|genres=").append(encodePart(encodeList(genres.map(::normalize))))
            append("|platforms=").append(encodePart(encodeList(platforms.map(::normalize))))
            append("|minRating=").append(minRating ?: "")
            append("|minYear=").append(minYear ?: "")
            append("|maxYear=").append(maxYear ?: "")
            val effectiveSort = if (query.isBlank() && !sort.isNullOrBlank()) {
                sort.trim().lowercase(Locale.ROOT)
            } else ""
            append("|sort=").append(effectiveSort)
        }
    }

    companion object {
        const val KEY_DISCOVER_TOP_RATED = "discover:top-rated"
        const val KEY_DISCOVER_TRENDING = "discover:trending"
        fun popular(type: String): String = "discover:popular:${normalize(type)}"

        const val KEY_PREFIX_SEARCH = "q:"

        const val SEARCH_TTL_SECONDS = 15 * 60L // 15 minutes
        const val DISCOVER_TTL_SECONDS = 60 * 60L // 60 minutes
        const val GAME_STALE_TTL_SECONDS = 24 * 60 * 60L // 24 hours
        const val MAX_BFF_OFFSET = 1000

        /**
         * Normalizes raw user input with trim, Unicode NFC, and lowercase.
         */
        fun normalize(rawQuery: String): String {
            val trimmed = rawQuery.trim()
            val nfc = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
            return nfc.lowercase(Locale.ROOT)
        }

        /**
         * Returns canonical search query key for raw text input.
         */
        fun search(rawQuery: String): String = Search(rawQuery).key

        /**
         * Maps domain [io.github.typenil.gametracker.core.model.GameSearchQuery] to canonical [Search] query key.
         */
        fun fromDomain(domainQuery: io.github.typenil.gametracker.core.model.GameSearchQuery): Search = Search(
            query = domainQuery.query,
            genres = domainQuery.genres,
            platforms = domainQuery.platforms,
            minRating = domainQuery.minRating,
            minYear = domainQuery.minYear,
            maxYear = domainQuery.maxYear,
            sort = domainQuery.sort,
        )

        private fun encodePart(value: String): String = "${value.length}:$value"

        private fun encodeList(values: List<String>): String =
            values.sorted().joinToString(separator = "") { encodePart(it) }
    }
}
