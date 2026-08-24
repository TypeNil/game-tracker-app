package io.github.typenil.gametracker.core.data.recommendations

import javax.inject.Inject

class NoOpLibrarySeeder @Inject constructor() : LibrarySeeder {
    override suspend fun seedIfEmpty() = Unit
}
