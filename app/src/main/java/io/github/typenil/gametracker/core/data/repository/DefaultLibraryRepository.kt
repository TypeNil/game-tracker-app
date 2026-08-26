package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.mapper.toDomain
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultLibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val transactionRunner: TransactionRunner,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LibraryRepository {

    override fun getLibraryGamesFlow(): Flow<List<LibraryGame>> =
        libraryDao.getPopulatedLibraryEntriesFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getLibraryEntryFlow(gameId: Long): Flow<LibraryEntry?> =
        libraryDao.getLibraryEntryFlow(gameId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                val game = gameDao.getGameById(gameId)
                    ?: return@runSuspendCatching AppResult.Error(
                        AppError.UnknownError(IllegalStateException("Parent game $gameId must exist before updating library")),
                    )
                val now = System.currentTimeMillis() / 1000
                val existing = libraryDao.getLibraryEntry(gameId)
                val updated = if (existing != null) {
                    existing.copy(status = status, updatedAtEpochSeconds = now)
                } else {
                    LibraryEntry(
                        gameId = gameId,
                        status = status,
                        addedAtEpochSeconds = now,
                        updatedAtEpochSeconds = now,
                    ).toEntity()
                }
                libraryDao.upsertLibraryEntry(updated)
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                val game = gameDao.getGameById(entry.gameId)
                    ?: return@runSuspendCatching AppResult.Error(
                        AppError.UnknownError(IllegalStateException("Parent game ${entry.gameId} must exist before updating library")),
                    )
                val now = System.currentTimeMillis() / 1000
                val clampedRating = entry.userRating?.coerceIn(1, 10)
                val clampedHours = entry.hoursPlayed.coerceAtLeast(0)
                val sanitizedNotes = entry.userNotes?.let { notes ->
                    if (notes.codePointCount(0, notes.length) > MAX_NOTES_CODE_POINTS) {
                        val endIdx = notes.offsetByCodePoints(0, MAX_NOTES_CODE_POINTS)
                        notes.substring(0, endIdx)
                    } else {
                        notes
                    }
                }
                val entity = entry.copy(
                    userRating = clampedRating,
                    hoursPlayed = clampedHours,
                    userNotes = sanitizedNotes,
                    updatedAtEpochSeconds = now,
                ).toEntity()
                libraryDao.upsertLibraryEntry(entity)
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun addToWishlist(game: Game): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                val now = System.currentTimeMillis() / 1000
                transactionRunner {
                    if (libraryDao.getLibraryEntry(game.id) == null) {
                        gameDao.upsertGame(game.toEntity(now))
                        libraryDao.upsertLibraryEntry(
                            LibraryEntry(
                                gameId = game.id,
                                status = LibraryStatus.WISHLIST,
                                addedAtEpochSeconds = now,
                                updatedAtEpochSeconds = now,
                            ).toEntity(),
                        )
                    }
                }
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun upsertUserEdits(
        gameId: Long,
        status: LibraryStatus,
        userRating: Int?,
        hoursPlayed: Int,
        userNotes: String?,
        isFavorite: Boolean,
    ): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                gameDao.getGameById(gameId) ?: return@runSuspendCatching AppResult.Error(
                    AppError.UnknownError(IllegalStateException("Parent game $gameId must exist")),
                )
                val existing = libraryDao.getLibraryEntry(gameId)
                    ?: return@runSuspendCatching AppResult.Error(
                        AppError.UnknownError(IllegalStateException("No library entry for $gameId")),
                    )
                val now = System.currentTimeMillis() / 1000
                val notes = userNotes?.trim()?.takeIf { it.isNotEmpty() }
                val sanitizedNotes = notes?.let { raw ->
                    if (raw.codePointCount(0, raw.length) > MAX_NOTES_CODE_POINTS) {
                        raw.substring(0, raw.offsetByCodePoints(0, MAX_NOTES_CODE_POINTS))
                    } else {
                        raw
                    }
                }
                libraryDao.upsertLibraryEntry(
                    existing.copy(
                        status = status,
                        userRating = userRating?.coerceIn(1, 10),
                        hoursPlayed = hoursPlayed.coerceAtLeast(0),
                        userNotes = sanitizedNotes,
                        isFavorite = isFavorite,
                        updatedAtEpochSeconds = now,
                    ),
                )
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                libraryDao.deleteLibraryEntry(gameId)
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    companion object {
        const val MAX_NOTES_CODE_POINTS = 500
    }
}
