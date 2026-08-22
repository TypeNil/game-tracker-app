package io.github.typenil.gametracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.typenil.gametracker.core.database.converter.GameDetailsColumnConverters
import io.github.typenil.gametracker.core.database.converter.GameTrackerTypeConverters
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.entity.GameDetailsEntity
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.entity.RemoteKeyEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef

/**
 * Main Room Database for GameTracker application (SSOT).
 */
@Database(
    entities = [
        GameEntity::class,
        SearchQueryEntity::class,
        SearchResultCrossRef::class,
        RemoteKeyEntity::class,
        LibraryEntryEntity::class,
        GameDetailsEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(GameTrackerTypeConverters::class, GameDetailsColumnConverters::class)
abstract class GameTrackerDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    abstract fun gameDetailsDao(): GameDetailsDao

    abstract fun searchDao(): SearchDao

    abstract fun remoteKeyDao(): RemoteKeyDao

    abstract fun libraryDao(): LibraryDao

    companion object {
        const val DATABASE_NAME = "gametracker.db"
    }
}
