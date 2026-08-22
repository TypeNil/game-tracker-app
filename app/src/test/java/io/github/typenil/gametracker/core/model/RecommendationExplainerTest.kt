package io.github.typenil.gametracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationExplainerTest {

    @Test
    fun topTwoPositiveFactors_becomeReasons() {
        val profile = RecommendationProfile(
            genreWeights = mapOf("RPG" to 1f),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = emptySet(),
            isColdStart = false,
        )
        val ranked = RankedRecommendation(
            candidate = RecommendationCandidate(
                gameId = 1,
                name = "G",
                genres = listOf("RPG"),
                similarToGameIds = listOf(9L),
                rating = 90.0,
                ratingCount = 100,
            ),
            score = 99f,
            factors = RecommendationFactors(
                genreOverlap = 1f,
                themeOverlap = 0f,
                platformOverlap = 0f,
                similarBoost = 1f,
                bayesianRating = 0.8f,
                negativePenalty = 0f,
                recency = 0f,
            ),
        )
        val reasons = RecommendationExplainer.explain(
            ranked,
            profile,
            RankerWeights(genreOverlap = 1f, similarBoost = 1.5f, rating = 0.4f, recency = 0f),
        )
        assertEquals(2, reasons.size)
        assertEquals(RecommendationReason.SimilarGame, reasons[0])
        assertEquals(RecommendationReason.GenreOverlap(listOf("RPG")), reasons[1])
    }

    @Test
    fun negativePenalty_isNeverAReason() {
        val profile = RecommendationProfile(
            genreWeights = mapOf("Sports" to -1f),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = emptySet(),
            isColdStart = false,
        )
        val ranked = RankedRecommendation(
            candidate = RecommendationCandidate(gameId = 1, name = "G", genres = listOf("Sports")),
            score = -1f,
            factors = RecommendationFactors(0f, 0f, 0f, 0f, 0f, negativePenalty = -1f, recency = 0f),
        )
        assertTrue(RecommendationExplainer.explain(ranked, profile).isEmpty())
    }
}

