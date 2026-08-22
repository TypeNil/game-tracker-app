package io.github.typenil.gametracker.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.migration.DatabaseMigrations
import io.github.typenil.gametracker.core.database.transaction.RoomTransactionRunner
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner

    companion object {
        @Provides
        @Singleton
        fun provideGameTrackerDatabase(
            @ApplicationContext context: Context
        ): GameTrackerDatabase {
            return Room.databaseBuilder(
                context,
                GameTrackerDatabase::class.java,
                GameTrackerDatabase.DATABASE_NAME
            )
                .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
                .build()
        }

        @Provides
        fun provideGameDao(database: GameTrackerDatabase): GameDao {
            return database.gameDao()
        }

        @Provides
        fun provideGameDetailsDao(database: GameTrackerDatabase): GameDetailsDao {
            return database.gameDetailsDao()
        }

        @Provides
        fun provideSearchDao(database: GameTrackerDatabase): SearchDao {
            return database.searchDao()
        }

        @Provides
        fun provideRemoteKeyDao(database: GameTrackerDatabase): RemoteKeyDao {
            return database.remoteKeyDao()
        }

        @Provides
        fun provideLibraryDao(database: GameTrackerDatabase): LibraryDao {
            return database.libraryDao()
        }
    }
}
