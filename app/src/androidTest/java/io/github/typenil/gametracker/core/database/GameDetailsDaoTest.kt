package io.github.typenil.gametracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.entity.CompanyColumn
import io.github.typenil.gametracker.core.database.entity.GameDetailsEntity
import io.github.typenil.gametracker.core.database.entity.ReleaseDateColumn
import io.github.typenil.gametracker.core.database.entity.SimilarGameColumn
import io.github.typenil.gametracker.core.database.entity.VideoColumn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GameDetailsDaoTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var gameDetailsDao: GameDetailsDao

    private val detailsEntity = GameDetailsEntity(
        gameId = 1942L,
        name = "The Witcher 3: Wild Hunt",
        coverUrl = "https://example.com/cover.jpg",
        rating = 93.7,
        totalRating = 92.7,
        totalRatingCount = 5451L,
        releaseDateEpochSeconds = 1431993600L,
        summary = "RPG masterpiece",
        url = "https://www.igdb.com/games/the-witcher-3-wild-hunt",
        genres = listOf("RPG"),
        themes = listOf("Fantasy", "Open world"),
        gameModes = listOf("Single player"),
        platforms = listOf("PC", "PS5"),
        releaseDates = listOf(
            ReleaseDateColumn(platform = "PC", dateEpochSeconds = 1431993600L, year = 2015),
            ReleaseDateColumn(platform = "Switch", year = 2021)
        ),
        companies = listOf(CompanyColumn(name = "CD Projekt RED", isDeveloper = true)),
        screenshots = listOf("https://example.com/shot1.jpg"),
        videos = listOf(VideoColumn(videoId = "abc123", name = "Trailer")),
        similarGames = listOf(SimilarGameColumn(id = 25076L, name = "Red Dead Redemption 2", totalRating = 93.6)),
        cachedAtEpochSeconds = 1700000000L
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDetailsDao = database.gameDetailsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertDetails_persistsAndReemitsThroughFlowWithNestedColumns() = runTest {
        gameDetailsDao.upsertDetails(detailsEntity)

        val stored = gameDetailsDao.getGameDetails(1942L)
        assertNotNull(stored)
        assertEquals("The Witcher 3: Wild Hunt", stored?.name)
        assertEquals(92.7, stored?.totalRating ?: 0.0, 0.001)
        assertEquals(5451L, stored?.totalRatingCount)
        // JSON TypeConverters must round-trip nested column types strictly
        assertEquals(listOf("Fantasy", "Open world"), stored?.themes)
        assertEquals(2, stored?.releaseDates?.size)
        assertEquals(1431993600L, stored?.releaseDates?.first()?.dateEpochSeconds)
        assertNull(stored?.releaseDates?.get(1)?.dateEpochSeconds)
        assertEquals(2021, stored?.releaseDates?.get(1)?.year)
        assertEquals("CD Projekt RED", stored?.companies?.single()?.name)
        assertTrue(stored?.companies?.single()?.isDeveloper == true)
        assertEquals("abc123", stored?.videos?.single()?.videoId)
        assertEquals(25076L, stored?.similarGames?.single()?.id)

        val flowValue = gameDetailsDao.getGameDetailsFlow(1942L).first()
        assertEquals(stored, flowValue)
    }

    @Test
    fun upsertDetails_replacesExistingRowForSameGame() = runTest {
        gameDetailsDao.upsertDetails(detailsEntity)
        gameDetailsDao.upsertDetails(detailsEntity.copy(totalRating = 90.0, cachedAtEpochSeconds = 1700000500L))

        val updated = gameDetailsDao.getGameDetails(1942L)
        assertEquals(90.0, updated?.totalRating ?: 0.0, 0.001)
        assertEquals(1700000500L, updated?.cachedAtEpochSeconds)
    }

    @Test
    fun getGameDetailsFlow_emitsNullForUnknownGame() = runTest {
        assertNull(gameDetailsDao.getGameDetailsFlow(999999L).first())
        assertNull(gameDetailsDao.getGameDetails(999999L))
    }

    @Test
    fun deleteStaleDetails_evictsOnlyRowsOlderThanThreshold() = runTest {
        gameDetailsDao.upsertDetails(detailsEntity.copy(gameId = 1L, cachedAtEpochSeconds = 100L))
        gameDetailsDao.upsertDetails(detailsEntity.copy(gameId = 2L, cachedAtEpochSeconds = 10_000L))

        val deleted = gameDetailsDao.deleteStaleDetails(staleThreshold = 500L)

        assertEquals(1, deleted)
        assertNull(gameDetailsDao.getGameDetails(1L))
        assertNotNull(gameDetailsDao.getGameDetails(2L))
    }
}
