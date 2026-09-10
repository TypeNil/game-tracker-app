package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.entity.PopulatedLibraryGameEntity
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface LibraryDao {

    companion object {
        const val LIBRARY_ENTRIES_BY_STATUS =
            "SELECT * FROM library_entries WHERE status = :status ORDER BY updatedAtEpochSeconds DESC"
    }

    @Upsert
    suspend fun upsertLibraryEntry(entry: LibraryEntryEntity): Long

    @Query("SELECT * FROM library_entries WHERE gameId = :gameId")
    fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntryEntity?>

    @Query("SELECT * FROM library_entries WHERE gameId = :gameId")
    suspend fun getLibraryEntry(gameId: Long): LibraryEntryEntity?

    @Query(LIBRARY_ENTRIES_BY_STATUS)
    fun getLibraryEntriesByStatusFlow(status: LibraryStatus): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries ORDER BY updatedAtEpochSeconds DESC")
    fun getAllLibraryEntriesFlow(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries")
    suspend fun getAllLibraryEntries(): List<LibraryEntryEntity>

    /**
     * Entries the user explicitly opted into release notifications for.
     * Filtering lives in SQL so notification eligibility is a single, testable predicate,
     * independent of [LibraryStatus].
     */
    @Query("SELECT * FROM library_entries WHERE releaseNotificationsEnabled = 1")
    suspend fun getEntriesWithReleaseNotificationsEnabled(): List<LibraryEntryEntity>

    @Query("SELECT * FROM library_entries WHERE isFavorite = 1 ORDER BY updatedAtEpochSeconds DESC")
    fun getFavoriteLibraryEntriesFlow(): Flow<List<LibraryEntryEntity>>

    @Transaction
    @Query("SELECT * FROM library_entries ORDER BY updatedAtEpochSeconds DESC")
    fun getPopulatedLibraryEntriesFlow(): Flow<List<PopulatedLibraryGameEntity>>


    @Query(
        """
        UPDATE library_entries
        SET isFavorite = CASE isFavorite WHEN 1 THEN 0 ELSE 1 END
        WHERE gameId = :gameId
        """,
    )
    suspend fun toggleFavorite(gameId: Long): Int

    @Query(
        """
        UPDATE library_entries
        SET status = :status,
            updatedAtEpochSeconds = :updatedAtEpochSeconds
        WHERE gameId = :gameId
        """,
    )
    suspend fun updateStatus(
        gameId: Long,
        status: LibraryStatus,
        updatedAtEpochSeconds: Long,
    ): Int

    @Query(
        """
        UPDATE library_entries
        SET hoursPlayed = :hoursPlayed,
            updatedAtEpochSeconds = :updatedAtEpochSeconds
        WHERE gameId = :gameId
        """,
    )
    suspend fun updateHoursPlayed(
        gameId: Long,
        hoursPlayed: Int,
        updatedAtEpochSeconds: Long,
    ): Int

    @Query("DELETE FROM library_entries WHERE gameId = :gameId")
    suspend fun deleteLibraryEntry(gameId: Long): Int
}
