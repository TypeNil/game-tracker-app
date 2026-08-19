package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user game library records.
 */
@Dao
interface LibraryDao {

    @Upsert
    suspend fun upsertLibraryEntry(entry: LibraryEntryEntity): Long

    @Query("SELECT * FROM library_entries WHERE gameId = :gameId")
    fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntryEntity?>

    @Query("SELECT * FROM library_entries WHERE gameId = :gameId")
    suspend fun getLibraryEntry(gameId: Long): LibraryEntryEntity?

    @Query("SELECT * FROM library_entries WHERE status = :status ORDER BY updatedAtEpochSeconds DESC")
    fun getLibraryEntriesByStatusFlow(status: LibraryStatus): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries ORDER BY updatedAtEpochSeconds DESC")
    fun getAllLibraryEntriesFlow(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries WHERE isFavorite = 1 ORDER BY updatedAtEpochSeconds DESC")
    fun getFavoriteLibraryEntriesFlow(): Flow<List<LibraryEntryEntity>>

    @Query("DELETE FROM library_entries WHERE gameId = :gameId")
    suspend fun deleteLibraryEntry(gameId: Long): Int
}
