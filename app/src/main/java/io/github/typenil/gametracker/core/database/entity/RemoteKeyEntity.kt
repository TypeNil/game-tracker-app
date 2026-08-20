package io.github.typenil.gametracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing pagination keys and metadata for Paging 3 RemoteMediator.
 */
@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val queryKey: String,
    val prevOffset: Int?,
    val nextOffset: Int?,
    val lastUpdatedEpochSeconds: Long
)
