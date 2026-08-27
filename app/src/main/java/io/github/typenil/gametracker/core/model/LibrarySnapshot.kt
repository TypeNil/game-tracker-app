package io.github.typenil.gametracker.core.model

sealed interface LibrarySnapshot {
    data object Loading : LibrarySnapshot

    data class Ready(
        val entries: Map<Long, LibraryEntry>,
    ) : LibrarySnapshot

    data class Failed(
        val error: AppError,
    ) : LibrarySnapshot
}
