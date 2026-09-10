package io.github.typenil.gametracker.core.data.backup

import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.mapper.toDomain
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryNotes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLibraryBackupRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val transactionRunner: TransactionRunner,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : LibraryBackupRepository {

    override suspend fun exportLibrary(): AppResult<ByteArray> = withContext(ioDispatcher) {
        runSuspendCatching {
            val (entries, games) = transactionRunner {
                libraryDao.getAllLibraryEntries() to gameDao.getGamesReferencedByLibrary()
            }
            val gamesById = games.associateBy { it.id }
            val missingParent = entries.firstOrNull { it.gameId !in gamesById }
            if (missingParent != null) {
                error("Parent game ${missingParent.gameId} missing for library export")
            }
            val backup = LibraryBackupFile(
                schemaVersion = LibraryBackupCodec.SCHEMA_VERSION,
                exportedAt = clock.instant(),
                items = entries.map { entry ->
                    LibraryBackupItem(
                        entry = entry.toDomain(),
                        game = gamesById.getValue(entry.gameId).toDomain(),
                    )
                },
            )
            LibraryBackupCodec.encode(backup)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(AppError.UnknownError(it)) },
        )
    }

    override suspend fun previewImport(file: LibraryBackupFile): AppResult<LibraryImportPreview> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                val localIds = libraryDao.getAllLibraryEntries().map { it.gameId }.toSet()
                val fileIds = file.items.map { it.entry.gameId }
                LibraryImportPreview(
                    foundCount = fileIds.size,
                    newCount = fileIds.count { it !in localIds },
                    conflictCount = fileIds.count { it in localIds },
                )
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(AppError.UnknownError(it)) },
            )
        }

    override suspend fun importLibrary(
        file: LibraryBackupFile,
        mode: LibraryImportMode,
    ): AppResult<Unit> = withContext(ioDispatcher) {
        runSuspendCatching {
            transactionRunner {
                if (mode == LibraryImportMode.REPLACE) {
                    libraryDao.deleteAllLibraryEntries()
                }
                val cachedAt = clock.instant().epochSecond
                file.items.forEach { item ->
                    if (gameDao.getGameById(item.game.id) == null) {
                        gameDao.upsertGame(item.game.toEntity(cachedAt))
                    }
                    libraryDao.upsertLibraryEntry(
                        item.entry.copy(
                            userNotes = item.entry.userNotes?.let(LibraryNotes::clamp),
                        ).toEntity(),
                    )
                }
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(AppError.UnknownError(it)) },
        )
    }
}
