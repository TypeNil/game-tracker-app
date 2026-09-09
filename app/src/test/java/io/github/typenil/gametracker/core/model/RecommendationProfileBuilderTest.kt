package io.github.typenil.gametracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationProfileBuilderTest {

    @Test
    fun empty_isColdStart_andEmptyWeights() {
        val profile = RecommendationProfileBuilder.build(emptyList())
        assertTrue(profile.isColdStart)
        assertTrue(profile.genreWeights.isEmpty())
        assertTrue(profile.themeWeights.isEmpty())
        assertTrue(profile.platformWeights.isEmpty())
        assertTrue(profile.excludedGameIds.isEmpty())
    }

    @Test
    fun wishlist_isAWeakPositiveSignal() {
        val profile = RecommendationProfileBuilder.build(
            listOf(signal(status = LibraryStatus.WISHLIST, genres = listOf("RPG")))
        )
        assertFalse(profile.isColdStart)
        assertEquals(1f, profile.genreWeights.getValue("RPG"))
    }


    @Test
    fun favorite_playing_andHighRating_stack_thenNormalize() {
        val profile = RecommendationProfileBuilder.build(
            listOf(
                signal(
                    gameId = 1,
                    status = LibraryStatus.PLAYING,
                    userRating = 10,
                    isFavorite = true,
                    genres = listOf("RPG", "RPG"),
                    themes = listOf("Fantasy"),
                    platforms = listOf("PC"),
                )
            )
        )
        assertEquals(1f, profile.genreWeights.getValue("RPG"))
        assertEquals(1f, profile.themeWeights.getValue("Fantasy"))
        assertTrue(profile.platformWeights.isEmpty())
        assertFalse(profile.isColdStart)
        assertTrue(profile.excludedGameIds.isEmpty())
    }

    @Test
    fun twoGenres_normalizeToMaxAbs() {
        val profile = RecommendationProfileBuilder.build(
            listOf(
                signal(gameId = 1, status = LibraryStatus.COMPLETED, genres = listOf("RPG")),
                signal(gameId = 2, status = LibraryStatus.COMPLETED, genres = listOf("RPG", "Shooter")),
            )
        )
        assertEquals(1f, profile.genreWeights.getValue("RPG"), 0.001f)
        assertEquals(0.5f, profile.genreWeights.getValue("Shooter"), 0.001f)
    }

    @Test
    fun notInterested_excludesAndPenalizes_noPositiveLeak() {
        val profile = RecommendationProfileBuilder.build(
            listOf(
                signal(
                    gameId = 9,
                    status = LibraryStatus.NOT_INTERESTED,
                    userRating = 10,
                    isFavorite = true,
                    genres = listOf("Sports"),
                )
            )
        )
        assertEquals(setOf(9L), profile.excludedGameIds)
        assertEquals(-1f, profile.genreWeights.getValue("Sports"))
        assertTrue(profile.isColdStart)
    }

    @Test
    fun droppedLowRating_penalizes_droppedHighRating_excludesOnly() {
        val profile = RecommendationProfileBuilder.build(
            listOf(
                signal(gameId = 1, status = LibraryStatus.DROPPED, userRating = 2, genres = listOf("Horror")),
                signal(gameId = 2, status = LibraryStatus.DROPPED, userRating = 9, genres = listOf("RPG")),
                signal(gameId = 3, status = LibraryStatus.DROPPED, userRating = null, genres = listOf("Racing")),
            )
        )
        assertEquals(setOf(1L, 2L, 3L), profile.excludedGameIds)
        assertEquals(-2f / 3f, profile.genreWeights.getValue("Horror"), 0.001f)
        assertEquals(1f, profile.genreWeights.getValue("RPG"))
        assertTrue("Racing" !in profile.genreWeights)
        assertFalse(profile.isColdStart)
    }

    @Test
    fun coldStart_fillsEmptyProfile_andBlendsUnderLibraryWeights() {
        val empty = RecommendationProfileBuilder.build(
            signals = emptyList(),
            coldStartGenres = setOf("Indie"),
            coldStartPlatforms = setOf("PC"),
        )
        assertTrue(empty.isColdStart)
        assertEquals(1f, empty.genreWeights.getValue("Indie"))
        assertEquals(1f, empty.platformWeights.getValue("PC"))
        assertTrue(empty.hasRankingSignal)

        val blended = RecommendationProfileBuilder.build(
            signals = listOf(signal(status = LibraryStatus.COMPLETED, genres = listOf("RPG"))),
            coldStartGenres = setOf("Indie"),
            coldStartPlatforms = setOf("PS5"),
        )
        assertFalse(blended.isColdStart)
        assertEquals(1f, blended.genreWeights.getValue("RPG"))
        assertEquals(
            RecommendationProfileBuilder.COLD_START / RecommendationProfileBuilder.PLAYING_OR_COMPLETED,
            blended.genreWeights.getValue("Indie"),
            0.0001f,
        )
        assertEquals(1f, blended.platformWeights.getValue("PS5"))
    }

    @Test
    fun coldStartBlend_decaysAsLibraryEvidenceGrows() {
        val oneGame = RecommendationProfileBuilder.build(
            signals = listOf(
                signal(gameId = 1, status = LibraryStatus.COMPLETED, genres = listOf("RPG")),
            ),
            coldStartGenres = setOf("Indie"),
        )
        val fiveGames = RecommendationProfileBuilder.build(
            signals = (1L..5L).map { id ->
                signal(gameId = id, status = LibraryStatus.COMPLETED, genres = listOf("RPG"))
            },
            coldStartGenres = setOf("Indie"),
        )
        val indieOne = oneGame.genreWeights.getValue("Indie")
        val indieFive = fiveGames.genreWeights.getValue("Indie")
        assertTrue(indieFive < indieOne)
        assertEquals(
            RecommendationProfileBuilder.COLD_START /
                (5 * RecommendationProfileBuilder.PLAYING_OR_COMPLETED),
            indieFive,
            0.0001f,
        )
    }

    @Test
    fun blankTags_andInvalidRating_ignored() {
        val profile = RecommendationProfileBuilder.build(
            listOf(
                signal(
                    status = LibraryStatus.COMPLETED,
                    userRating = 99,
                    genres = listOf(" ", "RPG"),
                    themes = listOf(""),
                    platforms = emptyList(),
                )
            )
        )
        assertEquals(setOf("RPG"), profile.genreWeights.keys)
        assertTrue(profile.themeWeights.isEmpty())
        assertEquals(1f, profile.genreWeights.getValue("RPG"))
    }

    @Test
    fun favoriteOnWishlist_isPositive() {
        val profile = RecommendationProfileBuilder.build(
            listOf(signal(status = LibraryStatus.WISHLIST, isFavorite = true, genres = listOf("RPG")))
        )
        assertEquals(1f, profile.genreWeights.getValue("RPG"))
        assertFalse(profile.isColdStart)
    }

    @Test
    fun completedLowRating_doesNotPenalize() {
        val profile = RecommendationProfileBuilder.build(
            listOf(signal(status = LibraryStatus.COMPLETED, userRating = 2, genres = listOf("RPG")))
        )
        assertEquals(1f, profile.genreWeights.getValue("RPG"))
        assertFalse(profile.isColdStart)
        assertTrue(profile.excludedGameIds.isEmpty())
    }

    @Test
    fun oneMultiplatGame_clearsPlatformWeights() {
        val profile = RecommendationProfileBuilder.build(
            listOf(
                signal(
                    status = LibraryStatus.COMPLETED,
                    genres = listOf("RPG"),
                    platforms = listOf("PC", "PlayStation 5", "Xbox Series X"),
                )
            )
        )
        assertFalse(profile.isColdStart)
        assertEquals(1f, profile.genreWeights.getValue("RPG"))
        assertTrue(profile.platformWeights.isEmpty())
    }

    @Test
    fun fivePs5AndOnePc_keepsOnlyPs5() {
        val signals = (1L..5L).map { id ->
            signal(
                gameId = id,
                status = LibraryStatus.COMPLETED,
                genres = listOf("RPG"),
                platforms = if (id == 1L) listOf("PlayStation 5", "PC") else listOf("PlayStation 5"),
            )
        }
        val profile = RecommendationProfileBuilder.build(signals)
        assertEquals(setOf("PlayStation 5"), profile.platformWeights.keys)
    }


    private fun signal(
        gameId: Long = 1L,
        status: LibraryStatus = LibraryStatus.WISHLIST,
        userRating: Int? = null,
        isFavorite: Boolean = false,
        genres: List<String> = emptyList(),
        themes: List<String> = emptyList(),
        platforms: List<String> = emptyList(),
    ) = RecommendationSignal(
        gameId = gameId,
        status = status,
        userRating = userRating,
        isFavorite = isFavorite,
        genres = genres,
        themes = themes,
        platforms = platforms,
    )
}
