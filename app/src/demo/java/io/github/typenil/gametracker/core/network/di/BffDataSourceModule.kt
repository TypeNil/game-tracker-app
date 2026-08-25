package io.github.typenil.gametracker.core.network.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.datasource.FakeBffDataSource
import io.github.typenil.gametracker.core.data.recommendations.DemoLibrarySeeder
import io.github.typenil.gametracker.core.data.recommendations.LibrarySeeder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BffDataSourceModule {
    @Binds
    @Singleton
    abstract fun bindBffRemoteDataSource(
        fakeDataSource: FakeBffDataSource
    ): BffRemoteDataSource

    @Binds
    abstract fun bindLibrarySeeder(impl: DemoLibrarySeeder): LibrarySeeder

}
