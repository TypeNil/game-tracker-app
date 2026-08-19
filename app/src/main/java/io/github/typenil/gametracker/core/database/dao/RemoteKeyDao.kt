package io.github.typenil.gametracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.typenil.gametracker.core.database.entity.RemoteKeyEntity

/**
 * Data Access Object for Paging 3 RemoteMediator pagination state.
 */
@Dao
interface RemoteKeyDao {

    @Upsert
    suspend fun upsert(remoteKey: RemoteKeyEntity): Long

    @Query("SELECT * FROM remote_keys WHERE queryKey = :queryKey")
    suspend fun getRemoteKey(queryKey: String): RemoteKeyEntity?

    @Query("DELETE FROM remote_keys WHERE queryKey = :queryKey")
    suspend fun deleteRemoteKey(queryKey: String): Int

    @Query("DELETE FROM remote_keys")
    suspend fun clearAllRemoteKeys(): Int
}
