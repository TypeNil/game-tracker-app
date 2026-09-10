package io.github.typenil.gametracker.feature.library.insights

import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryInsightsTest {

    @Test
    fun emptyLibrary_returnsZeroedInsights() {
        val insights = computeLibraryInsights(emptyList())

        assertEquals(0, insights.totalGames)
        assertEquals(0, insights.playingCount)
        assertEquals(0, insights.completedCount)
        assertEquals(0, insights.wishlistCount)
        assertEquals(0, insights.droppedCount)
        assertEquals(0, insights.favoritesCount)
        assertEquals(0L, insights.totalHours)
        assertNull(insights.averageUserRating)
        assertNull(insights.completionRate)
        assertTrue(insights.mostPlayed.isEmpty())
        assertTrue(insights.topGenres.isEmpty())
        assertTrue(insights.topPlatforms.isEmpty())
    }

    @Test
    fun notInterested_isExcludedFromEveryInsightMetric() {
        val included = libraryGame(
            id = 1L,
            name = "Hades",
            status = LibraryStatus.PLAYING,
            hoursPlayed = 12,
            userRating = 8,
            isFavorite = true,
            genres = listOf("Indie", "Adventure"),
            platforms = listOf("PC (Microsoft Windows)"),
        )
        val excluded = libraryGame(
            id = 99L,
            name = "Dominant Excluded",
            status = LibraryStatus.NOT_INTERESTED,
            hoursPlayed = 500,
            userRating = 10,
            isFavorite = true,
            genres = listOf("Shooter"),
            platforms = listOf("PlayStation 5"),
        )

        val withExcluded = computeLibraryInsights(listOf(included, excluded))
        val includedOnly = computeLibraryInsights(listOf(included))

        assertEquals(includedOnly, withExcluded)
    }

    @Test
    fun statusFavoriteAndHours_countVisiblePopulation() {
        val insights = computeLibraryInsights(
            listOf(
                libraryGame(id = 1L, name = "A", status = LibraryStatus.PLAYING, hoursPlayed = 10),
                libraryGame(
                    id = 2L,
                    name = "B",
                    status = LibraryStatus.COMPLETED,
                    hoursPlayed = 20,
                    isFavorite = true,
                ),
                libraryGame(id = 3L, name = "C", status = LibraryStatus.WISHLIST, hoursPlayed = 0),
                libraryGame(id = 4L, name = "D", status = LibraryStatus.DROPPED, hoursPlayed = 5),
                libraryGame(
                    id = 5L,
                    name = "E",
                    status = LibraryStatus.WISHLIST,
                    hoursPlayed = 7,
                    isFavorite = true,
                ),
            ),
        )

        assertEquals(5, insights.totalGames)
        assertEquals(1, insights.playingCount)
        assertEquals(1, insights.completedCount)
        assertEquals(2, insights.wishlistCount)
        assertEquals(1, insights.droppedCount)
        assertEquals(2, insights.favoritesCount)
        assertEquals(42L, insights.totalHours)
    }

    @Test
    fun wishlistRetainsHours_inTotalAndMostPlayed() {
        val insights = computeLibraryInsights(
            listOf(
                libraryGame(
                    id = 1L,
                    name = "Moved to wishlist",
                    status = LibraryStatus.WISHLIST,
                    hoursPlayed = 40,
                ),
            ),
        )

        assertEquals(40L, insights.totalHours)
        assertEquals(1, insights.mostPlayed.size)
        assertEquals(1L, insights.mostPlayed[0].gameId)
        assertEquals(40, insights.mostPlayed[0].hoursPlayed)
    }

    @Test
    fun averageUserRating_ignoresNulls_andNullWhenUnrated() {
        val unrated = computeLibraryInsights(
            listOf(libraryGame(id = 1L, name = "A", status = LibraryStatus.PLAYING)),
        )
        assertNull(unrated.averageUserRating)

        val rated = computeLibraryInsights(
            listOf(
                libraryGame(id = 1L, name = "A", status = LibraryStatus.PLAYING, userRating = 8),
                libraryGame(id = 2L, name = "B", status = LibraryStatus.COMPLETED, userRating = 10),
                libraryGame(id = 3L, name = "C", status = LibraryStatus.DROPPED),
            ),
        )
        assertEquals(9.0, rated.averageUserRating!!, 0.0)
    }

    @Test
    fun completionRate_isCompletedOverStarted_andUndefinedWithoutStartedGames() {
        val wishlistOnly = computeLibraryInsights(
            listOf(libraryGame(id = 1L, name = "Wish", status = LibraryStatus.WISHLIST)),
        )
        assertNull(wishlistOnly.completionRate)

        val started = computeLibraryInsights(
            listOf(
                libraryGame(id = 1L, name = "C1", status = LibraryStatus.COMPLETED),
                libraryGame(id = 2L, name = "C2", status = LibraryStatus.COMPLETED),
                libraryGame(id = 3L, name = "P", status = LibraryStatus.PLAYING),
                libraryGame(id = 4L, name = "D", status = LibraryStatus.DROPPED),
            ),
        )
        assertEquals(0.5, started.completionRate!!, 0.0)
    }

    @Test
    fun mostPlayed_dropsZeroHours_capsAtFive_andBreaksTiesById() {
        val games = (1L..6L).map { id ->
            libraryGame(
                id = id,
                name = "Game $id",
                status = LibraryStatus.PLAYING,
                hoursPlayed = if (id == 6L) 0 else 10,
            )
        } + libraryGame(
            id = 10L,
            name = "Tied hours lower id",
            status = LibraryStatus.COMPLETED,
            hoursPlayed = 10,
        )

        val mostPlayed = computeLibraryInsights(games).mostPlayed

        assertEquals(5, mostPlayed.size)
        assertTrue(mostPlayed.none { it.hoursPlayed == 0 })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), mostPlayed.map { it.gameId })
    }

    @Test
    fun topGenres_dedupePerGame_formatRpg_andBreakTiesAlphabetically() {
        val insights = computeLibraryInsights(
            listOf(
                libraryGame(
                    id = 1L,
                    name = "A",
                    status = LibraryStatus.PLAYING,
                    genres = listOf("Role-playing (RPG)", "RPG", "Adventure"),
                ),
                libraryGame(
                    id = 2L,
                    name = "B",
                    status = LibraryStatus.COMPLETED,
                    genres = listOf("Adventure", "Shooter"),
                ),
            ),
        )

        assertEquals(listOf("Adventure", "RPG", "Shooter"), insights.topGenres)
    }

    @Test
    fun topPlatforms_collapseFamiliesPerGame_andBreakTiesByEnumOrder() {
        val insights = computeLibraryInsights(
            listOf(
                libraryGame(
                    id = 1L,
                    name = "A",
                    status = LibraryStatus.PLAYING,
                    platforms = listOf("PlayStation 5", "PlayStation 4", "PC (Microsoft Windows)"),
                ),
                libraryGame(
                    id = 2L,
                    name = "B",
                    status = LibraryStatus.COMPLETED,
                    platforms = listOf("Xbox Series X|S", "Nintendo Switch"),
                ),
            ),
        )

        assertEquals(
            listOf(
                PlatformFamily.PLAYSTATION,
                PlatformFamily.XBOX,
                PlatformFamily.NINTENDO,
            ),
            insights.topPlatforms,
        )
    }

    @Test
    fun hugeHours_sumAsLongWithoutIntOverflow() {
        val insights = computeLibraryInsights(
            listOf(
                libraryGame(1, "A", LibraryStatus.PLAYING, hoursPlayed = Int.MAX_VALUE),
                libraryGame(2, "B", LibraryStatus.COMPLETED, hoursPlayed = Int.MAX_VALUE),
            ),
        )
        assertEquals(Int.MAX_VALUE.toLong() * 2, insights.totalHours)
        assertEquals(Int.MAX_VALUE, insights.mostPlayed.first().hoursPlayed)
    }

    @Test
    fun manyGenres_capsTasteAtThree_andKeepsLongFormattedLabel() {
        val longName = "Role Playing Game With A Very Long Name"
        val games = (1L..8L).map { id ->
            libraryGame(
                id = id,
                name = "G$id",
                status = LibraryStatus.COMPLETED,
                genres = listOf("genre-$id"),
            )
        } + libraryGame(
            id = 9L,
            name = "Long",
            status = LibraryStatus.PLAYING,
            hoursPlayed = 1,
            genres = listOf(longName, longName),
        )
        val insights = computeLibraryInsights(games)
        assertEquals(3, insights.topGenres.size)
        val longOnly = computeLibraryInsights(
            listOf(
                libraryGame(
                    id = 1L,
                    name = "Long",
                    status = LibraryStatus.PLAYING,
                    genres = listOf(longName),
                ),
            ),
        )
        assertEquals(listOf(formatGenreTag(longName)), longOnly.topGenres)
    }
}

private fun libraryGame(
    id: Long,
    name: String,
    status: LibraryStatus,
    hoursPlayed: Int = 0,
    userRating: Int? = null,
    isFavorite: Boolean = false,
    genres: List<String> = emptyList(),
    platforms: List<String> = emptyList(),
): LibraryGame = LibraryGame(
    game = Game(
        id = id,
        name = name,
        genres = genres,
        platforms = platforms,
    ),
    entry = LibraryEntry(
        gameId = id,
        status = status,
        userRating = userRating,
        isFavorite = isFavorite,
        addedAtEpochSeconds = 1L,
        updatedAtEpochSeconds = 1L,
        hoursPlayed = hoursPlayed,
    ),
)
