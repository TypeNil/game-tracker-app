package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.model.RecommendationSignal
import javax.inject.Inject

/**
 * Reads library rows from Room and joins catalog/details tags into [RecommendationSignal]s.
 * Caller owns threading and transactions so the three reads stay on one snapshot.
 */
class RoomRecommendationSignalCollector @Inject constructor(
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val gameDetailsDao: GameDetailsDao,
) {
    suspend fun collect(): List<RecommendationSignal> {
        val entries = libraryDao.getAllLibraryEntries()
        if (entries.isEmpty()) return emptyList()
        val ids = entries.map { it.gameId }
        val games = gameDao.getGamesByIds(ids).associateBy { it.id }
        val details = gameDetailsDao.getGameDetailsByIds(ids).associateBy { it.gameId }
        return entries.mapNotNull { entry ->
            val game = games[entry.gameId] ?: return@mapNotNull null
            val cached = details[entry.gameId]
            RecommendationSignal(
                gameId = entry.gameId,
                status = entry.status,
                userRating = entry.userRating,
                isFavorite = entry.isFavorite,
                genres = cached?.genres ?: game.genres,
                themes = cached?.themes.orEmpty(),
                platforms = cached?.platforms ?: game.platforms,
            )
        }
    }
}
