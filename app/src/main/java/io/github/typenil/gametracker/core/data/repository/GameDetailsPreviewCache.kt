package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameSummary
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime thread-safe preview cache for game details.
 *
 * Provides instantaneous frame-0 preview models for [io.github.typenil.gametracker.feature.details.GameDetailsViewModel]
 * when navigating from any catalog list (Discover rails, For You, Search, Library, Similar Games).
 *
 * Uses a pure Kotlin/Java LRU [LinkedHashMap] with synchronized access for thread safety
 * across background Flow mapping threads and the Main thread, without Android framework test stubs.
 */
enum class PreviewQuality {
    CATALOG,
    HYDRATED,
}

private data class Entry(
    val details: GameDetails,
    val quality: PreviewQuality,
)

@Suppress("TooManyFunctions")
@Singleton
class GameDetailsPreviewCache @Inject constructor() {

    private val lock = Any()
    private val map = object : LinkedHashMap<Long, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun putPreview(game: Game) {
        put(game.toDetailsPreview(), PreviewQuality.CATALOG)
    }

    fun putPreview(candidate: RecommendationCandidate) {
        put(candidate.toDetailsPreview(), PreviewQuality.CATALOG)
    }

    fun putPreview(summary: GameSummary) {
        put(summary.toDetailsPreview(), PreviewQuality.CATALOG)
    }

    fun putPreview(details: GameDetails) {
        put(details, PreviewQuality.CATALOG)
    }

    fun putHydrated(details: GameDetails) {
        put(details, PreviewQuality.HYDRATED)
    }

    fun put(game: Game) = putPreview(game)
    fun put(candidate: RecommendationCandidate) = putPreview(candidate)
    fun put(summary: GameSummary) = putPreview(summary)
    fun put(details: GameDetails) = putPreview(details)

    private fun put(details: GameDetails, quality: PreviewQuality) {
        synchronized(lock) {
            val existing = map[details.id]
            if (existing == null || quality >= existing.quality) {
                map[details.id] = Entry(details, quality)
            }
        }
    }

    fun get(id: Long): GameDetails? {
        return synchronized(lock) {
            map[id]?.details
        }
    }

    fun getQuality(id: Long): PreviewQuality? {
        return synchronized(lock) {
            map[id]?.quality
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }

    companion object {
        private const val INITIAL_CAPACITY = 32
        private const val LOAD_FACTOR = 0.75f
        private const val MAX_ENTRIES = 64
    }
}

/**
 * Converts a lightweight catalog [Game] domain model into a preliminary [GameDetails] preview
 * suitable for zero-frame rendering of the header while full details hydrate asynchronously.
 */
fun Game.toDetailsPreview(): GameDetails {
    return GameDetails(
        id = id,
        name = name,
        coverUrl = coverUrl,
        rating = rating,
        releaseDateEpochSeconds = releaseDateEpochSeconds,
        summary = summary,
        genres = genres,
        platforms = platforms
    )
}

fun RecommendationCandidate.toDetailsPreview(): GameDetails {
    return GameDetails(
        id = gameId,
        name = name,
        coverUrl = coverUrl,
        rating = rating,
        releaseDateEpochSeconds = releaseDateEpochSeconds,
        summary = summary,
        genres = genres,
        platforms = platforms
    )
}

fun GameSummary.toDetailsPreview(): GameDetails {
    return GameDetails(
        id = id,
        name = name.orEmpty(),
        coverUrl = coverUrl,
        rating = totalRating,
        genres = genres,
        platforms = platforms
    )
}
