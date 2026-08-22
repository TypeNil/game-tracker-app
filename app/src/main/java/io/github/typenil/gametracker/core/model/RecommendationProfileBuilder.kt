package io.github.typenil.gametracker.core.model

import io.github.typenil.gametracker.core.model.LibraryStatus.COMPLETED
import io.github.typenil.gametracker.core.model.LibraryStatus.DROPPED
import io.github.typenil.gametracker.core.model.LibraryStatus.NOT_INTERESTED
import io.github.typenil.gametracker.core.model.LibraryStatus.PLAYING
import kotlin.math.abs

/**
 * Pure builder: library signals + optional cold-start prefs → normalized profile.
 */
object RecommendationProfileBuilder {
    const val FAVORITE = 3f
    const val PLAYING_OR_COMPLETED = 2f
    const val EXPLICIT_NEGATIVE = 2f
    const val COLD_START = 1f
    const val HIGH_RATING_MIN = 7
    const val LOW_RATING_MAX = 4

    fun build(
        signals: List<RecommendationSignal>,
        coldStartGenres: Set<String> = emptySet(),
        coldStartPlatforms: Set<String> = emptySet(),
    ): RecommendationProfile {
        val excludedGameIds = signals
            .filter { it.status == NOT_INTERESTED || it.status == DROPPED }
            .mapTo(mutableSetOf()) { it.gameId }

        val genres = mutableMapOf<String, Float>()
        val themes = mutableMapOf<String, Float>()
        val platforms = mutableMapOf<String, Float>()
        var anyPositiveScalar = false

        for (signal in signals) {
            val rating = signal.userRating?.takeIf { it in 1..10 }
            val delta = when {
                signal.status == NOT_INTERESTED -> -EXPLICIT_NEGATIVE
                signal.status == DROPPED && rating != null && rating <= LOW_RATING_MAX ->
                    -EXPLICIT_NEGATIVE
                else -> {
                    var positive = 0f
                    if (signal.isFavorite) positive += FAVORITE
                    if (signal.status == PLAYING || signal.status == COMPLETED) {
                        positive += PLAYING_OR_COMPLETED
                    }
                    if (rating != null && rating >= HIGH_RATING_MIN) {
                        positive += (rating - 6)
                    }
                    if (positive > 0f) anyPositiveScalar = true
                    positive
                }
            }
            if (delta == 0f) continue
            apply(genres, signal.genres, delta)
            apply(themes, signal.themes, delta)
            apply(platforms, signal.platforms, delta)
        }

        fillColdStart(genres, coldStartGenres)
        fillColdStart(platforms, coldStartPlatforms)

        return RecommendationProfile(
            genreWeights = normalize(genres),
            themeWeights = normalize(themes),
            platformWeights = normalize(platforms),
            excludedGameIds = excludedGameIds,
            isColdStart = !anyPositiveScalar,
        )
    }

    private fun apply(target: MutableMap<String, Float>, tags: List<String>, delta: Float) {
        for (tag in distinctTags(tags)) {
            target[tag] = (target[tag] ?: 0f) + delta
        }
    }

    private fun fillColdStart(target: MutableMap<String, Float>, coldStart: Set<String>) {
        if (target.values.any { it > 0f }) return
        for (tag in distinctTags(coldStart)) {
            target[tag] = (target[tag] ?: 0f) + COLD_START
        }
    }

    private fun distinctTags(tags: Iterable<String>): Set<String> =
        tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun normalize(raw: Map<String, Float>): Map<String, Float> {
        val filtered = raw.filterValues { it != 0f }
        val maxAbs = filtered.values.maxOfOrNull { abs(it) } ?: return emptyMap()
        if (maxAbs == 0f) return emptyMap()
        return filtered.mapValues { (_, value) -> value / maxAbs }
    }
}
