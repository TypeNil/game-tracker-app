package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationExplainer
import io.github.typenil.gametracker.core.model.RecommendationProfile
import io.github.typenil.gametracker.core.model.RecommendationRanker
import io.github.typenil.gametracker.core.model.RecommendationReason
import io.github.typenil.gametracker.core.model.RecommendationSignal

data class DiscoverRecommendation(
    val game: Game,
    val reasons: List<RecommendationReason>,
)

data class DiscoverFeed(
    val recommendations: List<DiscoverRecommendation>,
    val trending: List<Game>,
)

object DiscoverFeedAssembler {

    fun topPositiveTags(weights: Map<String, Float>, limit: Int = 5): List<String> {
        return weights.filter { it.value > 0f }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Float>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    fun similarSeedIds(signals: List<RecommendationSignal>, limit: Int = 10): List<Long> {
        return signals
            .filter { it.isFavorite || it.status == LibraryStatus.PLAYING || it.status == LibraryStatus.COMPLETED }
            .sortedWith(compareByDescending<RecommendationSignal> { it.isFavorite }.thenBy { it.gameId })
            .map { it.gameId }
            .distinct()
            .take(limit)
    }

    fun assemble(
        profile: RecommendationProfile,
        candidates: List<RecommendationCandidate>,
        trending: List<Game>,
        nowEpochSeconds: Long,
        inLibraryIds: Set<Long> = emptySet(),
    ): DiscoverFeed {
        val ranked = RecommendationRanker.rank(profile, candidates, nowEpochSeconds)
        val recommendations = ranked.mapNotNull { item ->
            if (item.candidate.gameId in inLibraryIds) return@mapNotNull null
            DiscoverRecommendation(
                game = item.candidate.toGame(),
                reasons = RecommendationExplainer.explain(item, profile),
            )
        }
        val recIds = recommendations.map { it.game.id }.toSet()
        val hiddenFromTrending = recIds + profile.excludedGameIds
        return DiscoverFeed(
            recommendations = recommendations,
            trending = trending.filter { it.id !in hiddenFromTrending },
        )
    }

    private fun RecommendationCandidate.toGame(): Game {
        return Game(
            id = gameId,
            name = name,
            coverUrl = coverUrl,
            rating = rating,
            releaseDateEpochSeconds = releaseDateEpochSeconds,
            summary = summary,
            genres = genres,
            platforms = platforms,
        )
    }
}
