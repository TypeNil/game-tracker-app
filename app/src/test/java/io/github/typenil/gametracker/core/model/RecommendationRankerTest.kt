package io.github.typenil.gametracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationRankerTest {

    private val now = 1_700_000_000L

    @Test
    fun excludedIds_areDropped() {
        val ranked = RecommendationRanker.rank(
            profile(excluded = setOf(1L)),
            listOf(cand(1, genres = listOf("RPG")), cand(2, genres = listOf("RPG"))),
            now,
            weights = RankerWeights(
                genreOverlap = 1f,
                themeOverlap = 0f,
                platformOverlap = 0f,
                similarBoost = 0f,
                rating = 0f,
                negativePenalty = 0f,
            ),
        )
        assertEquals(listOf(2L), ranked.map { it.candidate.gameId })
    }

    @Test
    fun overlap_andSimilar_andNegative_composeScore() {
        val ranked = RecommendationRanker.rank(
            profile(genres = mapOf("RPG" to 1f, "Sports" to -1f)),
            listOf(cand(1, genres = listOf("RPG", "Sports"), similarTo = listOf(9L))),
            now,
            weights = RankerWeights(
                genreOverlap = 1f,
                themeOverlap = 0f,
                platformOverlap = 0f,
                similarBoost = 1.5f,
                rating = 0f,
                negativePenalty = 1f,
                recency = 0f,
            ),
        )
        val factors = ranked.single().factors
        assertEquals(1f, factors.genreOverlap, 0.001f)
        assertEquals(1f, factors.similarBoost, 0.001f)
        assertEquals(-1f, factors.negativePenalty, 0.001f)
        assertEquals(1f + 1.5f + -1f, ranked.single().score, 0.001f)
    }

    @Test
    fun bayesian_usesPriorWhenNoVotes() {
        val withVotes = RecommendationRanker.bayesian(90.0, 1000)
        val noVotes = RecommendationRanker.bayesian(90.0, 0)
        val missing = RecommendationRanker.bayesian(null, 10)
        assertTrue(withVotes > noVotes)
        assertEquals(0f, missing)
        assertEquals(
            ((0.0 / 10.0) * 90.0 + (10.0 / 10.0) * 70.0) / 100.0,
            noVotes.toDouble(),
            0.001,
        )
    }

    @Test
    fun tieBreak_isScoreThenId() {
        val ranked = RecommendationRanker.rank(
            profile(genres = mapOf("RPG" to 1f)),
            listOf(cand(20, genres = listOf("RPG")), cand(3, genres = listOf("RPG"))),
            now,
            weights = RankerWeights(
                genreOverlap = 1f,
                themeOverlap = 0f,
                platformOverlap = 0f,
                similarBoost = 0f,
                rating = 0f,
                negativePenalty = 0f,
            ),
        )
        assertEquals(listOf(3L, 20L), ranked.map { it.candidate.gameId })
    }

    @Test
    fun recency_usesInjectedNow() {
        val year = (365.25 * 86400).toLong()
        assertTrue(
            RecommendationRanker.recency(now - year, now) >
                RecommendationRanker.recency(now - 10 * year, now),
        )
        assertEquals(0f, RecommendationRanker.recency(null, now))
    }

    private fun profile(
        genres: Map<String, Float> = emptyMap(),
        themes: Map<String, Float> = emptyMap(),
        platforms: Map<String, Float> = emptyMap(),
        excluded: Set<Long> = emptySet(),
    ) = RecommendationProfile(genres, themes, platforms, excluded, isColdStart = false)

    private fun cand(
        id: Long,
        genres: List<String> = emptyList(),
        themes: List<String> = emptyList(),
        platforms: List<String> = emptyList(),
        similarTo: List<Long> = emptyList(),
        rating: Double? = null,
        ratingCount: Long? = null,
        release: Long? = null,
    ) = RecommendationCandidate(
        gameId = id,
        name = "G$id",
        genres = genres,
        themes = themes,
        platforms = platforms,
        similarToGameIds = similarTo,
        rating = rating,
        ratingCount = ratingCount,
        releaseDateEpochSeconds = release,
    )
}
