package io.github.typenil.gametracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationFeedTest {

    private val now = 1_700_000_000L
    private val feedWeights = RankerWeights(
        genreOverlap = 1f,
        themeOverlap = 0f,
        platformOverlap = 0f,
        similarBoost = 0f,
        rating = 0.4f,
        negativePenalty = 1f,
        recency = 0f,
    )

    @Test
    fun coldStart_prefersColdStartGenre() {
        val sports = cand(1, genres = listOf("Sports"))
        val indie = cand(2, genres = listOf("Indie"))
        val (profile, ranked) = feed(
            signals = emptyList(),
            candidates = listOf(sports, indie),
            coldStartGenres = setOf("Indie"),
        )
        assertTrue(profile.isColdStart)
        assertEquals(listOf(2L, 1L), ranked.map { it.candidate.gameId })
    }

    @Test
    fun completedRpg_promotesRpgOverSports() {
        val sports = cand(1, genres = listOf("Sports"))
        val rpg = cand(2, genres = listOf("RPG"))
        val pool = listOf(sports, rpg)

        val (_, before) = feed(signals = emptyList(), candidates = pool)
        assertEquals(listOf(1L, 2L), before.map { it.candidate.gameId })

        val completedRpg = RecommendationSignal(
            gameId = 99L,
            status = LibraryStatus.COMPLETED,
            genres = listOf("RPG"),
        )
        val (_, after) = feed(signals = listOf(completedRpg), candidates = pool)
        assertEquals(listOf(2L, 1L), after.map { it.candidate.gameId })
    }

    @Test
    fun notInterested_dropsThatGame() {
        val keep = cand(1, genres = listOf("RPG"))
        val rejected = cand(9, genres = listOf("RPG"))
        val (_, ranked) = feed(
            signals = listOf(
                RecommendationSignal(
                    gameId = 9L,
                    status = LibraryStatus.NOT_INTERESTED,
                    genres = listOf("Sports"),
                )
            ),
            candidates = listOf(keep, rejected),
        )
        assertEquals(listOf(1L), ranked.map { it.candidate.gameId })
    }

    @Test
    fun lowVotePerfectRating_doesNotBeatHighVoteGoodRating() {
        val ratingOnly = RankerWeights(
            genreOverlap = 0f,
            themeOverlap = 0f,
            platformOverlap = 0f,
            similarBoost = 0f,
            rating = 1f,
            negativePenalty = 0f,
            recency = 0f,
        )
        val lowVotes = cand(1, genres = listOf("RPG"), rating = 100.0, ratingCount = 0)
        val highVotes = cand(2, genres = listOf("RPG"), rating = 75.0, ratingCount = 1000)
        val (_, ranked) = feed(
            signals = emptyList(),
            candidates = listOf(lowVotes, highVotes),
            weights = ratingOnly,
        )
        assertEquals(2L, ranked.first().candidate.gameId)
        assertTrue(ranked[0].factors.bayesianRating > ranked[1].factors.bayesianRating)
    }

    @Test
    fun missingMetadata_doesNotThrow() {
        val bare = RecommendationCandidate(gameId = 5L, name = "Bare")
        val (profile, ranked) = feed(signals = emptyList(), candidates = listOf(bare))
        assertEquals(1, ranked.size)
        val reasons = RecommendationExplainer.explain(ranked.single(), profile, feedWeights)
        assertTrue(reasons.isEmpty())
    }

    @Test
    fun tieBreak_isStableById() {
        val (_, ranked) = feed(
            signals = listOf(
                RecommendationSignal(gameId = 99L, status = LibraryStatus.COMPLETED, genres = listOf("RPG"))
            ),
            candidates = listOf(cand(20, genres = listOf("RPG")), cand(3, genres = listOf("RPG"))),
        )
        assertEquals(listOf(3L, 20L), ranked.map { it.candidate.gameId })
    }

    @Test
    fun explanation_matchesTopScoreFactor() {
        val weights = RankerWeights(
            genreOverlap = 1f,
            themeOverlap = 0f,
            platformOverlap = 0f,
            similarBoost = 1.5f,
            rating = 0f,
            negativePenalty = 0f,
            recency = 0f,
        )
        val (profile, ranked) = feed(
            signals = listOf(
                RecommendationSignal(gameId = 99L, status = LibraryStatus.COMPLETED, genres = listOf("RPG"))
            ),
            candidates = listOf(
                cand(1, genres = listOf("RPG"), similarTo = listOf(99L))
            ),
            weights = weights,
        )
        val reasons = RecommendationExplainer.explain(ranked.single(), profile, weights)
        assertEquals(RecommendationReason.SimilarGame, reasons.first())
        assertTrue(reasons.size <= 2)
    }

    private fun feed(
        signals: List<RecommendationSignal>,
        candidates: List<RecommendationCandidate>,
        coldStartGenres: Set<String> = emptySet(),
        weights: RankerWeights = feedWeights,
    ): Pair<RecommendationProfile, List<RankedRecommendation>> {
        val profile = RecommendationProfileBuilder.build(signals, coldStartGenres = coldStartGenres)
        return profile to RecommendationRanker.rank(profile, candidates, now, weights)
    }

    private fun cand(
        id: Long,
        genres: List<String> = emptyList(),
        similarTo: List<Long> = emptyList(),
        rating: Double? = null,
        ratingCount: Long? = null,
    ) = RecommendationCandidate(
        gameId = id,
        name = "G$id",
        genres = genres,
        similarToGameIds = similarTo,
        rating = rating,
        ratingCount = ratingCount,
    )
}
