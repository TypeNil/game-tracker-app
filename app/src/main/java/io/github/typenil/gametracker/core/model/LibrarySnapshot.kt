package io.github.typenil.gametracker.core.model

sealed interface LibrarySnapshot {
    data object Loading : LibrarySnapshot

    /**
     * Library rows keyed by game id. Carries the whole [LibraryGame] rather than the bare
     * [LibraryEntry] because card actions and the edit sheet need the catalog side too
     * (release date decides whether release notifications can be offered at all).
     */
    data class Ready(
        val entries: Map<Long, LibraryGame>,
    ) : LibrarySnapshot

    data class Failed(
        val error: AppError,
    ) : LibrarySnapshot
}
