package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationProfile
import io.github.typenil.gametracker.core.model.RecommendationReason
import io.github.typenil.gametracker.core.model.RecommendationSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverFeedAssemblerTest {

    @Test
    fun assemble_ranksGenreOverlapRecommendations() {
        val profile = RecommendationProfile(
            genreWeights = mapOf("RPG" to 1f),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = emptySet(),
            isColdStart = false,
        )
        val candidates = listOf(candidate(10L, "Rec", genres = listOf("RPG")))

        val feed = DiscoverFeedAssembler.assemble(profile, candidates)

        assertEquals(listOf(10L), feed.recommendations.map { it.game.id })
        assertTrue(feed.recommendations.single().reasons.size <= 2)
        assertTrue(feed.recommendations.single().reasons.any { it is RecommendationReason.GenreOverlap })
    }

    @Test
    fun assemble_dropsInLibraryCandidatesFromForYou() {
        val profile = RecommendationProfile(
            genreWeights = mapOf("RPG" to 1f),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = emptySet(),
            isColdStart = false,
        )
        val candidates = listOf(
            candidate(10L, "Owned", genres = listOf("RPG")),
            candidate(11L, "New", genres = listOf("RPG")),
        )

        val feed = DiscoverFeedAssembler.assemble(
            profile,
            candidates,
            inLibraryIds = setOf(10L),
        )

        assertEquals(listOf(11L), feed.recommendations.map { it.game.id })
    }

    @Test
    fun similarSeedIds_includesWishlistAndPrefersFavorites() {
        val seeds = DiscoverFeedAssembler.similarSeedIds(
            listOf(
                signal(3L, favorite = false, status = LibraryStatus.PLAYING),
                signal(1L, favorite = true, status = LibraryStatus.COMPLETED),
                signal(2L, favorite = true, status = LibraryStatus.WISHLIST),
                signal(5L, favorite = false, status = LibraryStatus.WISHLIST),
                signal(4L, favorite = false, status = LibraryStatus.DROPPED),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L, 5L), seeds)
    }

    @Test
    fun assemble_skipsShownIdsThenWraps() {
        val profile = RecommendationProfile(
            genreWeights = mapOf("RPG" to 1f),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = emptySet(),
            isColdStart = false,
        )
        val candidates = listOf(
            candidate(10L, "A", listOf("RPG")),
            candidate(11L, "B", listOf("RPG")),
            candidate(12L, "C", listOf("RPG")),
        )

        val first = DiscoverFeedAssembler.assemble(
            profile, candidates, pageSize = 2,
        )
        val second = DiscoverFeedAssembler.assemble(
            profile,
            candidates,
            shownIds = first.recommendations.map { it.game.id }.toSet(),
            pageSize = 2,
        )
        val wrapped = DiscoverFeedAssembler.assemble(
            profile,
            candidates,
            shownIds = (first.recommendations + second.recommendations).map { it.game.id }.toSet(),
            pageSize = 2,
        )

        assertEquals(2, first.recommendations.size)
        assertTrue(second.recommendations.none { it.game.id in first.recommendations.map { rec -> rec.game.id } })
        assertEquals(2, wrapped.recommendations.size)
    }

    @Test
    fun assemble_capsOutputToForYouPageSize() {
        val profile = RecommendationProfile(
            genreWeights = mapOf("RPG" to 1f),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = emptySet(),
            isColdStart = false,
        )
        val candidates = (1L..30L).map { candidate(it, "G$it", listOf("RPG")) }
        val feed = DiscoverFeedAssembler.assemble(
            profile = profile,
            candidates = candidates,
        )
        assertEquals(DiscoverFeedAssembler.FOR_YOU_PAGE_SIZE, feed.recommendations.size)
        assertEquals(
            DiscoverFeedAssembler.FOR_YOU_PAGE_SIZE,
            feed.recommendations.map { it.game.id }.distinct().size,
        )
    }

    private fun candidate(id: Long, name: String, genres: List<String>) = RecommendationCandidate(
        gameId = id,
        name = name,
        genres = genres,
        rating = 80.0,
        ratingCount = 100L,
    )

    private fun signal(id: Long, favorite: Boolean, status: LibraryStatus) = RecommendationSignal(
        gameId = id,
        status = status,
        isFavorite = favorite,
    )
}
