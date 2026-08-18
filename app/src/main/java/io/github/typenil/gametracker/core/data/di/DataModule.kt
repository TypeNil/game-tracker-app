package io.github.typenil.gametracker.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.data.repository.DefaultGameRepository
import io.github.typenil.gametracker.core.data.repository.GameRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindGameRepository(
        impl: DefaultGameRepository
    ): GameRepository
}
