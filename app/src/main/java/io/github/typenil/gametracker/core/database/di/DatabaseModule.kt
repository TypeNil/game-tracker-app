package io.github.typenil.gametracker.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.RemoteKeyDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.transaction.RoomTransactionRunner
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGameTrackerDatabase(
        @ApplicationContext context: Context
    ): GameTrackerDatabase {
        return Room.databaseBuilder(
            context,
            GameTrackerDatabase::class.java,
            GameTrackerDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    fun provideGameDao(database: GameTrackerDatabase): GameDao {
        return database.gameDao()
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

    @Provides
    @Singleton
    fun provideTransactionRunner(database: GameTrackerDatabase): TransactionRunner {
        return RoomTransactionRunner(database)
    }
}
