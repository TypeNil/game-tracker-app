package io.github.typenil.gametracker.navigation

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.database.GameTrackerDatabase

/**
 * Debug-only graph seam for instrumentation fixtures. It is absent from release variants.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugNavigationFixtureEntryPoint {
    fun database(): GameTrackerDatabase
    fun gameRepository(): GameRepository
    fun libraryRepository(): LibraryRepository
    fun userPreferencesRepository(): UserPreferencesRepository
}
