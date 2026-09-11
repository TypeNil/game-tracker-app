package io.github.typenil.gametracker.devtools

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.typenil.gametracker.BuildConfig
import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.common.runSuspendCatching
import io.github.typenil.gametracker.core.data.backup.LibraryBackupCodec
import io.github.typenil.gametracker.core.data.backup.LibraryBackupFile
import io.github.typenil.gametracker.core.data.backup.LibraryBackupItem
import io.github.typenil.gametracker.core.data.backup.LibraryBackupRepository
import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.NotificationEventDao
import io.github.typenil.gametracker.core.database.dao.SearchDao
import io.github.typenil.gametracker.core.database.dao.SearchHistoryDao
import io.github.typenil.gametracker.core.database.mapper.toEntity
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.network.DebugBffUrlStore
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.mapper.toDomain
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Every data operation the developer tools expose. Deliberately **not** hidden behind an interface:
 * nothing outside `src/debug` can reference it, and the part worth substituting in tests (the pure
 * generator) is tested directly.
 *
 * Two rules the methods are written around:
 *
 * * seeding reuses the production import path, so a seed cannot drift from shipped Import semantics;
 * * destructive operations read their target back, so a wipe reports the state it actually reached.
 */
@Singleton
internal class DevToolsRepository @Inject constructor(
    private val libraryBackupRepository: LibraryBackupRepository,
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val gameDetailsDao: GameDetailsDao,
    private val searchDao: SearchDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val notificationEventDao: NotificationEventDao,
    private val database: GameTrackerDatabase,
    private val transactionRunner: TransactionRunner,
    private val remoteDataSource: BffRemoteDataSource,
    private val dataStore: DataStore<Preferences>,
    private val debugBffUrlStore: DebugBffUrlStore,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun seed(request: DevSeedRequest): AppResult<DevSeedOutcome> = withContext(ioDispatcher) {
        runSuspendCatching {
            val now = clock.instant()
            val catalog = if (request.preset.usesCatalog) fetchCatalog(request.count) else emptyList()
            val generated = DevSeedGenerator.generate(request, catalog, now.epochSecond)
            val imported = libraryBackupRepository.importLibrary(
                LibraryBackupFile(
                    schemaVersion = LibraryBackupCodec.SCHEMA_VERSION,
                    exportedAt = now,
                    items = generated.items,
                ),
                request.mode,
            )
            if (imported is AppResult.Error) {
                throw IllegalStateException("library import rejected the generated seed: ${imported.error}")
            }
            DevSeedOutcome(
                preset = request.preset,
                requestedCount = request.count,
                deliveredCount = generated.items.size,
                mode = request.mode,
                catalogHydrated = hydrateSyntheticCatalog(generated.syntheticItems),
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(AppError.UnknownError(it)) },
        )
    }

    suspend fun wipe(target: DevWipeTarget): AppResult<DevWipeOutcome> = withContext(ioDispatcher) {
        runSuspendCatching {
            when (target) {
                DevWipeTarget.LIBRARY -> wipeLibrary()
                DevWipeTarget.SEARCH_HISTORY -> wipeSearchHistory()
                DevWipeTarget.NOTIFICATION_LEDGER -> notificationEventDao.clearAllEvents()
                DevWipeTarget.ALL -> resetEverything()
            }
            DevWipeOutcome(
                target = target,
                libraryEntries = libraryDao.getAllLibraryEntries().size,
                searchHistoryEntries = searchHistoryDao.countSearchHistory(),
                notificationEvents = notificationEventDao.countEvents(),
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(AppError.UnknownError(it)) },
        )
    }

    suspend fun diagnostics(): AppResult<DevDiagnostics> = withContext(ioDispatcher) {
        runSuspendCatching {
            val entries = libraryDao.getAllLibraryEntries()
            // Opening the database is blocking work; this is the only place that needs it, and the
            // path/version are the only schema facts the panel can report honestly.
            val readable = database.openHelper.readableDatabase
            val databaseFile = File(readable.path.orEmpty())
            DevDiagnostics(
                applicationId = BuildConfig.APPLICATION_ID,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                flavor = BuildConfig.FLAVOR,
                buildType = BuildConfig.BUILD_TYPE,
                roomSchemaVersion = readable.version,
                databaseBytes = databaseFile.length(),
                writeAheadLogBytes = File("${databaseFile.path}-wal").length(),
                libraryEntries = entries.size,
                libraryEntriesByStatus = LibraryStatus.entries.associateWith { status ->
                    entries.count { it.status == status }
                },
                notificationEvents = notificationEventDao.countEvents(),
                searchHistoryEntries = searchHistoryDao.countSearchHistory(),
                bffOverride = debugBffUrlStore.currentUrl()?.toString().orEmpty(),
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(AppError.UnknownError(it)) },
        )
    }

    /** Catalog and caches survive: the library is user state, the catalog is a cache. */
    private suspend fun wipeLibrary() {
        val result = libraryBackupRepository.importLibrary(emptyBackupFile(), LibraryImportMode.REPLACE)
        if (result is AppResult.Error) {
            throw IllegalStateException("library wipe failed: ${result.error}")
        }
    }

    /**
     * One transaction: a half-applied clear would leave the recent-search UI empty while the legacy
     * `search_queries` table still resolves cached results.
     */
    private suspend fun wipeSearchHistory() {
        transactionRunner {
            searchHistoryDao.clearAllSearchHistory()
            searchDao.clearAllSearchHistory()
        }
    }

    /**
     * A confirmed reset must reach one final state even if the screen goes away mid-way, so the
     * sequence is non-cancellable. Cancellation is not swallowed for its own sake: the caller has
     * already confirmed, and the state on disk is the deliverable.
     *
     * Clearing `DataStore` also drops the demo first-run seed marker, so the demo flavor re-arms
     * exactly as it would after `pm clear`.
     */
    private suspend fun resetEverything() {
        withContext(NonCancellable) {
            // clearAllTables() is @WorkerThread and asserts it is not called inside a transaction.
            database.clearAllTables()
            dataStore.edit { it.clear() }
            debugBffUrlStore.setUrl(null as String?)
        }
    }

    private suspend fun fetchCatalog(count: Int): List<Game> {
        val wanted = count.coerceAtMost(CATALOG_MAX_OFFSET)
        val games = mutableListOf<Game>()
        var offset = 0
        while (games.size < wanted && offset < CATALOG_MAX_OFFSET) {
            val page = remoteDataSource.getTopRatedGames(limit = CATALOG_PAGE_SIZE, offset = offset).toDomain()
            if (page.isEmpty()) break
            games += page
            offset += page.size
        }
        return games.distinctBy { it.id }.take(wanted)
    }

    /**
     * Reserved ids resolve in no remote source, so the catalog cache has to be written locally or
     * every generated card would open the Details screen on its error state.
     *
     * Both rows are written here, parent first and in the same transaction: the production import
     * keeps an existing `games` row, so re-seeding the same reserved id with a different payload
     * would otherwise leave the library projection and the details screen describing two different
     * games.
     */
    private suspend fun hydrateSyntheticCatalog(items: List<LibraryBackupItem>): Boolean {
        if (items.isEmpty()) return true
        return runSuspendCatching {
            val cachedAt = clock.instant().epochSecond
            transactionRunner {
                items.forEach { gameDao.upsertGame(it.game.toEntity(cachedAt)) }
                items.forEach { gameDetailsDao.upsertDetails(it.game.toSyntheticDetails().toEntity(cachedAt)) }
            }
        }.isSuccess
    }

    private fun emptyBackupFile(): LibraryBackupFile = LibraryBackupFile(
        schemaVersion = LibraryBackupCodec.SCHEMA_VERSION,
        exportedAt = clock.instant(),
        items = emptyList(),
    )

    private companion object {
        /** The catalog endpoint caps a page at 30; three pages are plenty for a manual seed. */
        const val CATALOG_PAGE_SIZE = 30
        const val CATALOG_MAX_OFFSET = 90
    }
}
