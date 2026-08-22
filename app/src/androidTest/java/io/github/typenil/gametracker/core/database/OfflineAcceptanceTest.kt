package io.github.typenil.gametracker.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.data.repository.DefaultGameRepository
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.transaction.RoomTransactionRunner
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.model.CompanyDto
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.ReleaseDateDto
import io.github.typenil.gametracker.core.network.model.SimilarGameDto
import io.github.typenil.gametracker.core.network.model.VideoDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OfflineAcceptanceTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDao: GameDao
    private lateinit var gameDetailsDao: GameDetailsDao
    private lateinit var searchDao: SearchDao
    private lateinit var remoteKeyDao: RemoteKeyDao
    private lateinit var libraryDao: LibraryDao
    private lateinit var repository: DefaultGameRepository
    private lateinit var testRemoteDataSource: TestRemoteDataSource

    private val testNow = 1_700_000_000L

    /**
     * Single shared scheduler: the repository's ioDispatcher and each runTest scope
     * must use the same TestCoroutineScheduler, otherwise combine/yield inside the
     * details flow detects "different schedulers" and throws.
     */
    private val testScheduler = TestCoroutineScheduler()

    private val topRatedDtos = listOf(
        GameDto(id = 10L, name = "Elden Ring", coverUrl = "https://example.com/er.jpg", rating = 96.0, releaseDateEpochSeconds = 1645747200L, summary = "FromSoftware", genres = listOf("Action", "RPG"), platforms = listOf("PC", "PS5")),
        GameDto(id = 20L, name = "Baldur's Gate 3", coverUrl = "https://example.com/bg3.jpg", rating = 96.0, releaseDateEpochSeconds = 1691020800L, summary = "Larian Studios", genres = listOf("RPG"), platforms = listOf("PC", "PS5")),
        GameDto(id = 30L, name = "Zelda: Tears of the Kingdom", coverUrl = "https://example.com/totk.jpg", rating = 96.0, releaseDateEpochSeconds = 1683849600L, summary = "Nintendo", genres = listOf("Adventure"), platforms = listOf("Switch"))
    )

    private val searchDtos = listOf(
        GameDto(id = 100L, name = "The Witcher 3: Wild Hunt", coverUrl = "https://example.com/w3.jpg", rating = 94.0, releaseDateEpochSeconds = 1431993600L, summary = "CD Projekt Red", genres = listOf("RPG"), platforms = listOf("PC", "PS5")),
        GameDto(id = 101L, name = "The Witcher 2: Assassins of Kings", coverUrl = "https://example.com/w2.jpg", rating = 88.0, releaseDateEpochSeconds = 1305590400L, summary = "CD Projekt Red", genres = listOf("RPG"), platforms = listOf("PC"))
    )

    private val detailsDtos = listOf(
        GameDetailsDto(
            id = 10L,
            name = "Elden Ring",
            coverUrl = "https://example.com/er.jpg",
            rating = 96.0,
            releaseDateEpochSeconds = 1645747200L,
            summary = "FromSoftware",
            genres = listOf("Action", "RPG"),
            platforms = listOf("PC", "PS5"),
            url = "https://www.igdb.com/games/elden-ring",
            totalRating = 96.5,
            totalRatingCount = 1024L,
            themes = listOf("Fantasy", "Open world"),
            gameModes = listOf("Single player", "Multiplayer"),
            releaseDates = listOf(ReleaseDateDto(platform = "PC", dateEpochSeconds = 1645747200L, year = 2022)),
            companies = listOf(
                CompanyDto(name = "FromSoftware", isDeveloper = true),
                CompanyDto(name = "Bandai Namco Entertainment", isPublisher = true)
            ),
            screenshots = listOf("https://example.com/er-shot1.jpg", "https://example.com/er-shot2.jpg"),
            videos = listOf(VideoDto(videoId = "abc123", name = "Gameplay Trailer")),
            similarGames = listOf(
                SimilarGameDto(id = 20L, name = "Baldur's Gate 3", coverUrl = "https://example.com/bg3.jpg", totalRating = 95.0)
            )
        )
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        gameDao = database.gameDao()
        gameDetailsDao = database.gameDetailsDao()
        searchDao = database.searchDao()
        remoteKeyDao = database.remoteKeyDao()
        libraryDao = database.libraryDao()

        testRemoteDataSource = TestRemoteDataSource(topRatedDtos, searchDtos, detailsDtos)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        repository = DefaultGameRepository(
            remoteDataSource = testRemoteDataSource,
            gameDao = gameDao,
            gameDetailsDao = gameDetailsDao,
            searchDao = searchDao,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = RoomTransactionRunner(database),
            ioDispatcher = testDispatcher,
            nowEpochSeconds = { testNow }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun offlineAcceptance_endToEndDataFlow() = runTest(testScheduler) {
        // Step 1: Online sync for Discover (Top Rated) and Search
        val refreshDiscoverResult = repository.refreshTopRatedGames(limit = 20, offset = 0)
        assertTrue("Discover refresh must succeed online", refreshDiscoverResult is AppResult.Success)

        val discoverGames = repository.getTopRatedGamesFlow().first()
        assertEquals(3, discoverGames.size)
        assertEquals(10L, discoverGames[0].id)
        assertEquals(20L, discoverGames[1].id)
        assertEquals(30L, discoverGames[2].id)

        val searchResult = repository.searchGames(query = "witcher", limit = 20, offset = 0)
        assertTrue("Search refresh must succeed online", searchResult is AppResult.Success)

        val searchGames = repository.getSearchResultsFlow("witcher").first()
        assertEquals(2, searchGames.size)
        assertEquals(100L, searchGames[0].id)
        assertEquals(101L, searchGames[1].id)

        // Step 2: Airplane Mode Transition (Offline)
        testRemoteDataSource.isOffline = true

        val offlineRefreshDiscover = repository.refreshTopRatedGames(limit = 20, offset = 0)
        assertTrue(offlineRefreshDiscover is AppResult.Error)
        assertEquals(AppError.NetworkError, (offlineRefreshDiscover as AppResult.Error).error)

        val offlineSearch = repository.searchGames(query = "witcher", limit = 20, offset = 0)
        assertTrue(offlineSearch is AppResult.Error)
        assertEquals(AppError.NetworkError, (offlineSearch as AppResult.Error).error)

        // Step 3: Re-open cached data offline (SSOT order preserved)
        val cachedDiscover = repository.getTopRatedGamesFlow().first()
        assertEquals(3, cachedDiscover.size)
        assertEquals(10L, cachedDiscover[0].id)
        assertEquals(20L, cachedDiscover[1].id)
        assertEquals(30L, cachedDiscover[2].id)

        val cachedSearch = repository.getSearchResultsFlow("witcher").first()
        assertEquals(2, cachedSearch.size)
        assertEquals(100L, cachedSearch[0].id)
        assertEquals(101L, cachedSearch[1].id)

        // Step 4: Modify local library offline
        val libraryEntry = LibraryEntryEntity(
            gameId = 20L,
            status = LibraryStatus.COMPLETED,
            userRating = 10,
            userNotes = "Finished in Airplane Mode",
            isFavorite = true,
            addedAtEpochSeconds = testNow,
            updatedAtEpochSeconds = testNow,
            hoursPlayed = 120
        )
        libraryDao.upsertLibraryEntry(libraryEntry)

        val savedEntry = libraryDao.getLibraryEntry(20L)
        assertNotNull(savedEntry)
        assertEquals(LibraryStatus.COMPLETED, savedEntry?.status)
        assertEquals(10, savedEntry?.userRating)
        assertEquals("Finished in Airplane Mode", savedEntry?.userNotes)
        assertEquals(true, savedEntry?.isFavorite)
        assertEquals(120, savedEntry?.hoursPlayed)

        val favoriteEntries = libraryDao.getFavoriteLibraryEntriesFlow().first()
        assertEquals(1, favoriteEntries.size)
        assertEquals(20L, favoriteEntries[0].gameId)

        // Step 5: Data Protection & Eviction Safety
        // Insert an unreferenced orphan game with old timestamp
        val orphanGame = GameEntity(
            id = 999L,
            name = "Orphan Game",
            coverUrl = null,
            rating = null,
            releaseDateEpochSeconds = null,
            summary = null,
            genres = emptyList(),
            platforms = emptyList(),
            cachedAtEpochSeconds = 1L
        )
        gameDao.upsertGame(orphanGame)
        assertNotNull(gameDao.getGameById(999L))

        // Trigger cache cleanup with threshold = 500L
        repository.clearStaleCache(staleThresholdSeconds = 500L)

        // Orphan game should be evicted
        assertNull("Orphan game must be evicted by clearStaleCache", gameDao.getGameById(999L))

        // Active catalog games and user's library game must remain intact
        assertNotNull("Discover game in library must not be evicted", gameDao.getGameById(20L))
        assertNotNull("Search game must not be evicted", gameDao.getGameById(100L))

        // Direct delete of library game must throw SQLiteConstraintException due to ForeignKey.RESTRICT
        var constraintViolated = false
        try {
            database.openHelper.writableDatabase.execSQL("DELETE FROM games WHERE id = 20")
        } catch (e: SQLiteConstraintException) {
            constraintViolated = true
        }
        assertTrue("Expected SQLiteConstraintException due to ForeignKey.RESTRICT", constraintViolated)
        assertNotNull("Game with library entry must still exist in DB", gameDao.getGameById(20L))
    }

    @Test
    fun offlineAcceptance_detailsFirstOpenOfflineFallsBackToCatalogSkeleton() = runTest(testScheduler) {
        // Online: sync Discover so the slim catalog row exists in `games`
        val refreshDiscover = repository.refreshTopRatedGames(limit = 20, offset = 0)
        assertTrue("Discover refresh must succeed online", refreshDiscover is AppResult.Success)

        // Airplane mode BEFORE details were ever fetched
        testRemoteDataSource.isOffline = true

        val refreshDetails = repository.refreshGameDetails(10L)
        assertTrue(refreshDetails is AppResult.Error)
        assertEquals(AppError.NetworkError, (refreshDetails as AppResult.Error).error)

        // First open offline must still render the header from the catalog row
        val skeleton = repository.getGameDetailsFlow(10L).first()
        assertNotNull("Skeleton must be emitted from the catalog row", skeleton)
        assertEquals("Elden Ring", skeleton?.name)
        assertNull("Aggregate rating is unknown before hydration", skeleton?.totalRating)
        assertTrue(skeleton?.screenshots.isNullOrEmpty())
        assertTrue(skeleton?.similarGames.isNullOrEmpty())
        assertTrue(skeleton?.companies.isNullOrEmpty())
    }

    @Test
    fun offlineAcceptance_detailsHydrationSurvivesOfflineReopenWithFreshTtl() = runTest(testScheduler) {
        assertTrue(repository.refreshTopRatedGames(limit = 20, offset = 0) is AppResult.Success)
        assertTrue(repository.refreshGameDetails(10L) is AppResult.Success)

        val hydrated = repository.getGameDetailsFlow(10L).first()
        assertEquals("Elden Ring", hydrated?.name)
        assertEquals(96.5, hydrated?.totalRating ?: 0.0, 0.001)
        assertTrue(hydrated?.screenshots?.size == 2)
        assertTrue(hydrated?.companies?.any { it.isDeveloper } == true)

        testRemoteDataSource.isOffline = true
        // Fresh TTL: refresh returns Success without touching the dead network
        val offlineRefresh = repository.refreshGameDetails(10L)
        assertTrue("Fresh cache must skip network", offlineRefresh is AppResult.Success)

        val cached = repository.getGameDetailsFlow(10L).first()
        assertEquals(hydrated, cached)
    }

    private class TestRemoteDataSource(
        private val topRated: List<GameDto>,
        private val search: List<GameDto>,
        private val details: List<GameDetailsDto>
    ) : BffRemoteDataSource {
        var isOffline = false

        override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> {
            if (isOffline) throw IOException("Simulated Airplane Mode")
            return topRated
        }

        override suspend fun searchGames(query: String, limit: Int, offset: Int): List<GameDto> {
            if (isOffline) throw IOException("Simulated Airplane Mode")
            return search
        }

        override suspend fun getGameDetails(id: Long): GameDetailsDto {
            if (isOffline) throw IOException("Simulated Airplane Mode")
            return details.firstOrNull { it.id == id }
                ?: throw IOException("Game not found")
        }
    }
}
