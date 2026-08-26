package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.RecommendationCandidate
import io.github.typenil.gametracker.core.model.RecommendationProfile
import io.github.typenil.gametracker.core.model.RecommendationReason
import io.github.typenil.gametracker.core.model.RecommendationSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverFeedAssemblerTest {

    private val now = 1_780_000_000L

    @Test
    fun assemble_coldStart_keepsTrendingAndDropsExcluded() {
        val profile = RecommendationProfile(
            genreWeights = emptyMap(),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = setOf(2L),
            isColdStart = true,
        )
        val trending = listOf(game(1L, "A"), game(2L, "Excluded"), game(3L, "C"))

        val feed = DiscoverFeedAssembler.assemble(profile, emptyList(), trending, now)

        assertTrue(feed.recommendations.isEmpty())
        assertEquals(listOf(1L, 3L), feed.trending.map { it.id })
    }

    @Test
    fun assemble_dropsTrendingIdsThatAppearInRecs() {
        val profile = RecommendationProfile(
            genreWeights = mapOf("RPG" to 1f),
            themeWeights = emptyMap(),
            platformWeights = emptyMap(),
            excludedGameIds = emptySet(),
            isColdStart = false,
        )
        val candidates = listOf(candidate(10L, "Rec", genres = listOf("RPG")))
        val trending = listOf(game(10L, "Rec"), game(11L, "Trend"))

        val feed = DiscoverFeedAssembler.assemble(profile, candidates, trending, now)

        assertEquals(listOf(10L), feed.recommendations.map { it.game.id })
        assertEquals(listOf(11L), feed.trending.map { it.id })
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
            trending = emptyList(),
            nowEpochSeconds = now,
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
            profile, candidates, emptyList(), now, pageSize = 2,
        )
        val second = DiscoverFeedAssembler.assemble(
            profile,
            candidates,
            emptyList(),
            now,
            shownIds = first.recommendations.map { it.game.id }.toSet(),
            pageSize = 2,
        )
        val wrapped = DiscoverFeedAssembler.assemble(
            profile,
            candidates,
            emptyList(),
            now,
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
            trending = emptyList(),
            nowEpochSeconds = now,
        )
        assertEquals(DiscoverFeedAssembler.FOR_YOU_PAGE_SIZE, feed.recommendations.size)
        assertEquals(
            DiscoverFeedAssembler.FOR_YOU_PAGE_SIZE,
            feed.recommendations.map { it.game.id }.distinct().size,
        )
    }


    private fun game(id: Long, name: String) = Game(id = id, name = name)

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
