package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.entity.GameDetailsEntity
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRecommendationSignalCollectorTest {

    private val libraryDao: LibraryDao = mockk()
    private val gameDao: GameDao = mockk()
    private val gameDetailsDao: GameDetailsDao = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private val collector = RoomRecommendationSignalCollector(
        libraryDao = libraryDao,
        gameDao = gameDao,
        gameDetailsDao = gameDetailsDao,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun collect_emptyLibrary_returnsEmpty_withoutIdQueries() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns emptyList()

        assertTrue(collector.collect().isEmpty())

        coVerify(exactly = 0) { gameDao.getGamesByIds(any()) }
        coVerify(exactly = 0) { gameDetailsDao.getGameDetailsByIds(any()) }
    }

    @Test
    fun collect_prefersDetailsTags_fallsBackToCatalog_skipsMissingGame() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            entry(gameId = 1),
            entry(gameId = 2),
            entry(gameId = 3),
        )
        coEvery { gameDao.getGamesByIds(listOf(1, 2, 3)) } returns listOf(
            game(id = 1, genres = listOf("CatalogGenre"), platforms = listOf("CatalogPlat")),
            game(id = 2, genres = listOf("OnlyCatalog"), platforms = listOf("Switch")),
        )
        coEvery { gameDetailsDao.getGameDetailsByIds(listOf(1, 2, 3)) } returns listOf(
            details(
                gameId = 1,
                genres = listOf("DetailGenre"),
                themes = listOf("Fantasy"),
                platforms = listOf("PC"),
            ),
        )

        val signals = collector.collect()
        assertEquals(2, signals.size)

        val first = signals.first { it.gameId == 1L }
        assertEquals(listOf("DetailGenre"), first.genres)
        assertEquals(listOf("Fantasy"), first.themes)
        assertEquals(listOf("PC"), first.platforms)

        val second = signals.first { it.gameId == 2L }
        assertEquals(listOf("OnlyCatalog"), second.genres)
        assertTrue(second.themes.isEmpty())
        assertEquals(listOf("Switch"), second.platforms)
    }

    private fun entry(gameId: Long) = LibraryEntryEntity(
        gameId = gameId,
        status = LibraryStatus.WISHLIST,
        userRating = null,
        userNotes = null,
        isFavorite = false,
        addedAtEpochSeconds = 0L,
        updatedAtEpochSeconds = 0L,
        hoursPlayed = 0,
    )

    private fun game(
        id: Long,
        genres: List<String>,
        platforms: List<String>,
    ) = GameEntity(
        id = id,
        name = "Game $id",
        coverUrl = null,
        rating = null,
        releaseDateEpochSeconds = null,
        summary = null,
        genres = genres,
        platforms = platforms,
        cachedAtEpochSeconds = 0L,
    )

    private fun details(
        gameId: Long,
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
    ) = GameDetailsEntity(
        gameId = gameId,
        name = "Game $gameId",
        coverUrl = null,
        rating = null,
        totalRating = null,
        totalRatingCount = null,
        releaseDateEpochSeconds = null,
        summary = null,
        url = null,
        genres = genres,
        themes = themes,
        gameModes = emptyList(),
        platforms = platforms,
        releaseDates = emptyList(),
        companies = emptyList(),
        screenshots = emptyList(),
        videos = emptyList(),
        similarGames = emptyList(),
        cachedAtEpochSeconds = 0L,
    )
}
