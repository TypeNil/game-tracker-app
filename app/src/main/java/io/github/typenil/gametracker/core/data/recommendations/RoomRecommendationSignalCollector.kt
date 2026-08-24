package io.github.typenil.gametracker.core.data.recommendations

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.model.RecommendationSignal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads library rows from Room and joins catalog/details tags into [RecommendationSignal]s.
 */
class RoomRecommendationSignalCollector @Inject constructor(
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val gameDetailsDao: GameDetailsDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun collect(): List<RecommendationSignal> = withContext(ioDispatcher) {
        val entries = libraryDao.getAllLibraryEntries()
        if (entries.isEmpty()) return@withContext emptyList()
        val ids = entries.map { it.gameId }
        val games = gameDao.getGamesByIds(ids).associateBy { it.id }
        val details = gameDetailsDao.getGameDetailsByIds(ids).associateBy { it.gameId }
        entries.mapNotNull { entry ->
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
