package io.github.typenil.gametracker.core.data.repository

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.mapper.toDomain
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.data.recommendations.RoomRecommendationSignalCollector
import io.github.typenil.gametracker.core.model.RecommendationSignal

import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryNotes

import io.github.typenil.gametracker.core.model.LibraryGame
import io.github.typenil.gametracker.core.model.LibraryStatus
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultLibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val transactionRunner: TransactionRunner,
    private val signalCollector: RoomRecommendationSignalCollector,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC(),
    private val previewCache: GameDetailsPreviewCache = GameDetailsPreviewCache(),
) : LibraryRepository {


    override fun getLibraryGamesFlow(): Flow<AppResult<List<LibraryGame>>> =
        libraryDao.getPopulatedLibraryEntriesFlow()
            .map { list ->
                list.map { entry ->
                    entry.toDomain().also { previewCache.putPreview(it.game) }
                }
            }
            .asAppResult()
            .flowOn(ioDispatcher)

    override fun getLibraryEntryFlow(gameId: Long): Flow<AppResult<LibraryEntry?>> =
        libraryDao.getLibraryEntryFlow(gameId)
            .map { it?.toDomain() }
            .asAppResult()
            .flowOn(ioDispatcher)


    override suspend fun setGameStatus(gameId: Long, status: LibraryStatus): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                transactionRunner {
                    val now = System.currentTimeMillis() / 1000
                    if (libraryDao.updateStatus(gameId, status, now) == 1) {
                        return@transactionRunner AppResult.Success(Unit)
                    }
                    gameDao.getGameById(gameId)
                        ?: return@transactionRunner AppResult.Error(
                            AppError.UnknownError(
                                IllegalStateException(
                                    "Parent game $gameId must exist before updating library",
                                ),
                            ),
                        )
                    libraryDao.upsertLibraryEntry(
                        LibraryEntry(
                            gameId = gameId,
                            status = status,
                            addedAtEpochSeconds = now,
                            updatedAtEpochSeconds = now,
                        ).toEntity(),
                    )
                    AppResult.Success(Unit)
                }
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun saveLibraryEntry(entry: LibraryEntry): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                transactionRunner {
                    gameDao.getGameById(entry.gameId)
                        ?: return@transactionRunner AppResult.Error(
                            AppError.UnknownError(
                                IllegalStateException(
                                    "Parent game ${entry.gameId} must exist before updating library",
                                ),
                            ),
                        )
                    val now = clock.instant().epochSecond
                    val existing = libraryDao.getLibraryEntry(entry.gameId)
                    val entity = entry.copy(
                        userRating = entry.userRating?.coerceIn(1, 10),
                        hoursPlayed = entry.hoursPlayed.coerceAtLeast(0),
                        userNotes = entry.userNotes?.let(LibraryNotes::clamp),
                        addedAtEpochSeconds = existing?.addedAtEpochSeconds ?: now,
                        updatedAtEpochSeconds = now,
                    ).toEntity()
                    libraryDao.upsertLibraryEntry(entity)
                    AppResult.Success(Unit)
                }
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
                transactionRunner {
                    gameDao.getGameById(gameId)
                        ?: return@transactionRunner AppResult.Error(
                            AppError.UnknownError(
                                IllegalStateException("Parent game $gameId must exist"),
                            ),
                        )
                    val existing = libraryDao.getLibraryEntry(gameId)
                        ?: return@transactionRunner AppResult.Error(
                            AppError.UnknownError(
                                IllegalStateException("No library entry for $gameId"),
                            ),
                        )
                    val now = System.currentTimeMillis() / 1000
                    val notes = userNotes?.trim()?.takeIf { it.isNotEmpty() }
                    val sanitizedNotes = notes?.let(LibraryNotes::clamp)

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
                }
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun toggleFavorite(gameId: Long): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                val updatedRows = libraryDao.toggleFavorite(
                    gameId = gameId,
                )
                if (updatedRows == 1) {
                    AppResult.Success(Unit)
                } else {
                    AppResult.Error(
                        AppError.UnknownError(
                            IllegalStateException("No library entry for $gameId"),
                        ),
                    )
                }
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun updateHoursPlayed(gameId: Long, hoursPlayed: Int): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                val clampedHours = hoursPlayed.coerceIn(0, 999_999)
                val now = System.currentTimeMillis() / 1000
                val updatedRows = libraryDao.updateHoursPlayed(
                    gameId = gameId,
                    hoursPlayed = clampedHours,
                    updatedAtEpochSeconds = now,
                )
                if (updatedRows == 1) {
                    AppResult.Success(Unit)
                } else {
                    AppResult.Error(
                        AppError.UnknownError(
                            IllegalStateException("No library entry for $gameId"),
                        ),
                    )
                }
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }

    override suspend fun removeGameFromLibrary(gameId: Long): AppResult<Unit> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                libraryDao.deleteLibraryEntry(gameId)
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Error(AppError.UnknownError(it)) }
        }


    override suspend fun getRecommendationSignals(): AppResult<List<RecommendationSignal>> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                transactionRunner { signalCollector.collect() }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(AppError.UnknownError(it)) },
            )
        }
}

private fun <T> Flow<T>.asAppResult(): Flow<AppResult<T>> =
    map<T, AppResult<T>> { value -> AppResult.Success(value) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(AppResult.Error(AppError.UnknownError(error)))
        }


