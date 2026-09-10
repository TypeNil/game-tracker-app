package io.github.typenil.gametracker.core.data.di

import dagger.Binds
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.typenil.gametracker.core.data.backup.ContentResolverDocumentBytesStore
import io.github.typenil.gametracker.core.data.backup.DefaultLibraryBackupRepository
import io.github.typenil.gametracker.core.data.backup.DocumentBytesStore
import io.github.typenil.gametracker.core.data.backup.LibraryBackupRepository
import io.github.typenil.gametracker.core.data.preferences.DataStoreUserPreferencesRepository
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.data.repository.DefaultGameRepository
import io.github.typenil.gametracker.core.data.repository.DefaultLibraryRepository
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides
        @Singleton
        fun provideUserPreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES_FILE) },
        )

        private const val USER_PREFERENCES_FILE = "user_preferences"
    }

    @Binds
    @Singleton
    abstract fun bindGameRepository(
        impl: DefaultGameRepository
    ): GameRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        impl: DefaultLibraryRepository
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindLibraryBackupRepository(
        impl: DefaultLibraryBackupRepository,
    ): LibraryBackupRepository

    @Binds
    @Singleton
    abstract fun bindDocumentBytesStore(
        impl: ContentResolverDocumentBytesStore,
    ): DocumentBytesStore

}
