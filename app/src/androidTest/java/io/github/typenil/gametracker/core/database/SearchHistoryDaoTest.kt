package io.github.typenil.gametracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.SearchHistoryDao
import io.github.typenil.gametracker.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SearchHistoryDaoTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var searchHistoryDao: SearchHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        searchHistoryDao = database.searchHistoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun observeRecentSearchQueries_returnsOrderedByLastQueriedDescending() = runTest {
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("witcher", "Witcher", 100L))
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("cyberpunk", "Cyberpunk 2077", 300L))
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("zelda", "Zelda", 200L))

        val recent = searchHistoryDao.observeRecentSearchQueries(limit = 2).first()
        assertEquals(listOf("Cyberpunk 2077", "Zelda"), recent)
    }

    @Test
    fun upsertSearchHistory_updatesTimestampOnDuplicateNormalizedQuery() = runTest {
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("witcher", "witcher", 100L))
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("witcher", "The Witcher", 500L))

        val recent = searchHistoryDao.observeRecentSearchQueries(limit = 10).first()
        assertEquals(1, recent.size)
        assertEquals("The Witcher", recent[0])
    }

    @Test
    fun deleteSearchHistory_removesMatchingQuery() = runTest {
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("witcher", "Witcher", 100L))
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("zelda", "Zelda", 200L))

        searchHistoryDao.deleteSearchHistory("witcher")

        val recent = searchHistoryDao.observeRecentSearchQueries(limit = 10).first()
        assertEquals(listOf("Zelda"), recent)
    }

    @Test
    fun clearAllSearchHistory_removesAllEntries() = runTest {
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("witcher", "Witcher", 100L))
        searchHistoryDao.upsertSearchHistory(SearchHistoryEntity("zelda", "Zelda", 200L))

        searchHistoryDao.clearAllSearchHistory()

        val recent = searchHistoryDao.observeRecentSearchQueries(limit = 10).first()
        assertEquals(emptyList<String>(), recent)
    }
}
