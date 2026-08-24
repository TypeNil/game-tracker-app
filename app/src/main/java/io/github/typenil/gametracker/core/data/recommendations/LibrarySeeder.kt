package io.github.typenil.gametracker.core.data.recommendations

interface LibrarySeeder {
    suspend fun seedIfEmpty()
}
