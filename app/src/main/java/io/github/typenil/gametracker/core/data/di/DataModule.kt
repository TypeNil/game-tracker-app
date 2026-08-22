package io.github.typenil.gametracker.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.data.repository.DefaultGameRepository
import io.github.typenil.gametracker.core.data.repository.DefaultLibraryRepository
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindGameRepository(
        impl: DefaultGameRepository
    ): GameRepository

    @Binds
    abstract fun bindLibraryRepository(
        impl: DefaultLibraryRepository
    ): LibraryRepository
}
