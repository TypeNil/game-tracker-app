package io.github.typenil.gametracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.entity.RemoteKeyEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RemoteKeyDaoTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var remoteKeyDao: RemoteKeyDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remoteKeyDao = database.remoteKeyDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertAndGetRemoteKey_returnsCorrectOffsets() = runTest {
        val key = RemoteKeyEntity(
            queryKey = "discover:top-rated",
            prevOffset = null,
            nextOffset = 20,
            lastUpdatedEpochSeconds = 1000L
        )

        remoteKeyDao.upsert(key)

        val retrieved = remoteKeyDao.getRemoteKey("discover:top-rated")
        assertNotNull(retrieved)
        assertEquals("discover:top-rated", retrieved?.queryKey)
        assertNull(retrieved?.prevOffset)
        assertEquals(20, retrieved?.nextOffset)
        assertEquals(1000L, retrieved?.lastUpdatedEpochSeconds)
    }

    @Test
    fun deleteRemoteKey_removesSpecificKey() = runTest {
        remoteKeyDao.upsert(RemoteKeyEntity("key1", null, 20, 100L))
        remoteKeyDao.upsert(RemoteKeyEntity("key2", 20, 40, 100L))

        remoteKeyDao.deleteRemoteKey("key1")

        assertNull(remoteKeyDao.getRemoteKey("key1"))
        assertNotNull(remoteKeyDao.getRemoteKey("key2"))
    }
}
