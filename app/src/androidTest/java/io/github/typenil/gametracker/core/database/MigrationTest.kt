package io.github.typenil.gametracker.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.typenil.gametracker.core.database.migration.DatabaseMigrations.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GameTrackerDatabase::class.java
    )

    @Test
    fun migration1To2_preservesExistingDataAndSetsDefaultHoursPlayed() {
        var db = helper.createDatabase(testDbName, 1)

        // Populate v1 with raw SQL
        db.execSQL(
            """
            INSERT INTO games (id, name, coverUrl, rating, releaseDateEpochSeconds, summary, genres, platforms, cachedAtEpochSeconds)
            VALUES (1, 'The Witcher 3', 'https://example.com/w3.jpg', 95.0, 1430000000, 'Geralt of Rivia', '["RPG"]', '["PC", "PS5"]', 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO search_queries (query, createdAtEpochSeconds, lastQueriedAtEpochSeconds, resultCount)
            VALUES ('discover:top-rated', 1000, 1000, 1)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO search_results (query, gameId, position)
            VALUES ('discover:top-rated', 1, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO library_entries (gameId, status, userRating, userNotes, isFavorite, addedAtEpochSeconds, updatedAtEpochSeconds)
            VALUES (1, 'PLAYING', 10, 'Great RPG', 1, 1000, 1000)
            """.trimIndent()
        )
        db.close()

        // Run migration to schema v2 and validate schema identity
        db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // Validate migrated data integrity
        val gameCursor = db.query("SELECT id, name, rating FROM games WHERE id = 1")
        assertTrue(gameCursor.moveToFirst())
        assertEquals(1L, gameCursor.getLong(0))
        assertEquals("The Witcher 3", gameCursor.getString(1))
        assertEquals(95.0, gameCursor.getDouble(2), 0.001)
        gameCursor.close()

        val libraryCursor = db.query(
            "SELECT gameId, status, userRating, userNotes, isFavorite, hoursPlayed FROM library_entries WHERE gameId = 1"
        )
        assertTrue(libraryCursor.moveToFirst())
        assertEquals(1L, libraryCursor.getLong(0))
        assertEquals("PLAYING", libraryCursor.getString(1))
        assertEquals(10, libraryCursor.getInt(2))
        assertEquals("Great RPG", libraryCursor.getString(3))
        assertEquals(1, libraryCursor.getInt(4))
        assertEquals(0, libraryCursor.getInt(5)) // hoursPlayed defaults to 0
        libraryCursor.close()

        val searchCursor = db.query("SELECT query, gameId, position FROM search_results WHERE query = 'discover:top-rated'")
        assertTrue(searchCursor.moveToFirst())
        assertEquals("discover:top-rated", searchCursor.getString(0))
        assertEquals(1L, searchCursor.getLong(1))
        assertEquals(0, searchCursor.getInt(2))
        searchCursor.close()

        db.close()
    }

    @Test
    fun migration1To2_deduplicatesOverlappingPositionsAndEnforcesUniqueConstraint() {
        var db = helper.createDatabase(testDbName, 1)

        db.execSQL(
            """
            INSERT INTO games (id, name, coverUrl, rating, releaseDateEpochSeconds, summary, genres, platforms, cachedAtEpochSeconds)
            VALUES (1, 'Game 1', NULL, NULL, NULL, NULL, '[]', '[]', 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO games (id, name, coverUrl, rating, releaseDateEpochSeconds, summary, genres, platforms, cachedAtEpochSeconds)
            VALUES (2, 'Game 2', NULL, NULL, NULL, NULL, '[]', '[]', 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO search_queries (query, createdAtEpochSeconds, lastQueriedAtEpochSeconds, resultCount)
            VALUES ('q:duplicate', 1000, 1000, 2)
            """.trimIndent()
        )
        // Insert duplicate positions in v1
        db.execSQL("INSERT INTO search_results (query, gameId, position) VALUES ('q:duplicate', 1, 0)")
        db.execSQL("INSERT INTO search_results (query, gameId, position) VALUES ('q:duplicate', 2, 0)")
        db.close()

        // Run migration
        db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // Verify deduplication: exactly 1 row for position 0
        val countCursor = db.query("SELECT COUNT(*) FROM search_results WHERE query = 'q:duplicate' AND position = 0")
        assertTrue(countCursor.moveToFirst())
        assertEquals(1, countCursor.getInt(0))
        countCursor.close()

        // Verify UNIQUE constraint enforcement on (query, position)
        var constraintViolated = false
        try {
            db.execSQL("INSERT INTO search_results (query, gameId, position) VALUES ('q:duplicate', 2, 0)")
        } catch (e: SQLiteConstraintException) {
            constraintViolated = true
            assertNotNull(e.message)
        }
        assertTrue("Expected SQLiteConstraintException due to UNIQUE constraint on (query, position)", constraintViolated)

        db.close()
    }
}
