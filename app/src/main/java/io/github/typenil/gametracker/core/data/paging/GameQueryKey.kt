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

    data class Search(
        val query: String,
        val sort: String? = null,
        val platform: Int? = null
    ) : GameQueryKey {
        override val key: String = buildString {
            append(KEY_PREFIX_SEARCH)
            append(normalize(query))
            if (!sort.isNullOrBlank()) {
                append("|sort=")
                append(sort.trim().lowercase(Locale.ROOT))
            }
            if (platform != null) {
                append("|platform=")
                append(platform)
            }
        }
    }

    companion object {
        const val KEY_DISCOVER_TOP_RATED = "discover:top-rated"
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
        fun search(rawQuery: String): String {
            return KEY_PREFIX_SEARCH + normalize(rawQuery)
        }
    }
}
