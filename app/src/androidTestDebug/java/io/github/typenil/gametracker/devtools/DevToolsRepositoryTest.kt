package io.github.typenil.gametracker.devtools

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.data.backup.DefaultLibraryBackupRepository
import io.github.typenil.gametracker.core.data.backup.LibraryImportMode
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.database.entity.NotificationEventEntity
import io.github.typenil.gametracker.core.database.entity.SearchHistoryEntity
import io.github.typenil.gametracker.core.database.entity.SearchQueryEntity
import io.github.typenil.gametracker.core.database.entity.SearchResultCrossRef
import io.github.typenil.gametracker.core.database.transaction.RoomTransactionRunner
import io.github.typenil.gametracker.core.database.transaction.TransactionRunner
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryNotes
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.network.DebugBffUrlStore
import io.github.typenil.gametracker.core.network.datasource.BffRemoteDataSource
import io.github.typenil.gametracker.core.network.model.GameDetailsDto
import io.github.typenil.gametracker.core.network.model.GameDto
import io.github.typenil.gametracker.core.network.model.RecommendationCandidateDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives [DevToolsRepository] against a real Room database. Everything asserted here is read back
 * through the same DAOs the app reads, so a passing test means the UI sees the same state.
 */
@RunWith(AndroidJUnit4::class)
class DevToolsRepositoryTest {

    private lateinit var database: GameTrackerDatabase
    private lateinit var preferences: FakePreferencesDataStore
    private lateinit var bffUrlStore: DebugBffUrlStore
    private var nowSeconds: Instant = Instant.parse("2026-09-10T12:00:00Z")

    private val clock: Clock = object : Clock() {
        override fun instant(): Instant = nowSeconds
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameTrackerDatabase::class.java).build()
        preferences = FakePreferencesDataStore()
        bffUrlStore = DebugBffUrlStore(context)
        bffUrlStore.setUrl(null as String?)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(transactionRunner: TransactionRunner = RoomTransactionRunner(database)) =
        DevToolsRepository(
            libraryBackupRepository = DefaultLibraryBackupRepository(
                libraryDao = database.libraryDao(),
                gameDao = database.gameDao(),
                transactionRunner = transactionRunner,
                ioDispatcher = Dispatchers.IO,
                clock = clock,
            ),
            libraryDao = database.libraryDao(),
            gameDao = database.gameDao(),
            gameDetailsDao = database.gameDetailsDao(),
            searchDao = database.searchDao(),
            searchHistoryDao = database.searchHistoryDao(),
            notificationEventDao = database.notificationEventDao(),
            database = database,
            transactionRunner = transactionRunner,
            remoteDataSource = FakeCatalogSource(),
            dataStore = preferences,
            debugBffUrlStore = bffUrlStore,
            clock = clock,
            ioDispatcher = Dispatchers.IO,
        )

    private fun seedRequest(
        preset: DevSeedPreset,
        count: Int = preset.defaultCount,
        mode: LibraryImportMode = LibraryImportMode.REPLACE,
    ) = DevSeedRequest(preset, count, seed = 7L, mode = mode)

    @Test
    fun seedReplace_writesEveryEntryWithItsCatalogAndDetailsRow() = runTest {
        val outcome = (repository().seed(seedRequest(DevSeedPreset.EDGE)) as AppResult.Success).data

        assertTrue(outcome.catalogHydrated)
        assertEquals(DevSeedPreset.EDGE.defaultCount, outcome.deliveredCount)

        val entries = database.libraryDao().getAllLibraryEntries()
        assertEquals(DevSeedPreset.EDGE.defaultCount, entries.size)
        entries.forEach { entry ->
            assertNotNull(
                "library entry ${entry.gameId} has no parent catalog row",
                database.gameDao().getGameById(entry.gameId),
            )
            assertNotNull(
                "library entry ${entry.gameId} would open Details on an error",
                database.gameDetailsDao().getGameDetails(entry.gameId),
            )
        }
    }

    @Test
    fun seed_appliesTheSameNotesClampAsARealImport() = runTest {
        repository().seed(seedRequest(DevSeedPreset.EDGE))

        val longestNote = database.libraryDao().getAllLibraryEntries()
            .mapNotNull { it.userNotes }
            .maxOf { it.length }

        assertEquals(LibraryNotes.MAX_CODE_POINTS, longestNote)
    }

    @Test
    fun seedReplace_dropsLocalEntriesAndMergeKeepsThem() = runTest {
        val repository = repository()
        insertLocalLibraryEntry(LOCAL_GAME_ID)

        repository.seed(seedRequest(DevSeedPreset.EDGE))
        assertNull(database.libraryDao().getLibraryEntry(LOCAL_GAME_ID))

        insertLocalLibraryEntry(LOCAL_GAME_ID)
        repository.seed(seedRequest(DevSeedPreset.EDGE, mode = LibraryImportMode.MERGE))
        assertNotNull(database.libraryDao().getLibraryEntry(LOCAL_GAME_ID))
    }

    /**
     * The production import keeps an existing `games` row, so a reserved id re-seeded with a newer
     * payload would otherwise leave the catalog row and the details row describing two different
     * games: the library list and the Details screen would disagree about the same id.
     */
    @Test
    fun reseedingAfterTheClockMoves_keepsCatalogAndDetailsOnTheSameGame() = runTest {
        val repository = repository()
        repository.seed(seedRequest(DevSeedPreset.NOTIFICATIONS))
        nowSeconds = nowSeconds.plusSeconds(TEN_DAYS_SECONDS)

        repository.seed(seedRequest(DevSeedPreset.NOTIFICATIONS))

        val id = syntheticId(DevSeedPreset.NOTIFICATIONS, 1)
        val catalog = database.gameDao().getGameById(id)
        val details = database.gameDetailsDao().getGameDetails(id)
        assertNotNull(catalog)
        assertNotNull(details)
        assertEquals(catalog?.name, details?.name)
        assertEquals(catalog?.releaseDateEpochSeconds, details?.releaseDateEpochSeconds)
        assertEquals(nowSeconds.epochSecond, details?.releaseDateEpochSeconds)
    }

    @Test
    fun seed_whenCatalogHydrationFails_reportsAPartialSeedAndKeepsTheLibrary() = runTest {
        val repository = repository(transactionRunner = FailAfterFirstTransaction(RoomTransactionRunner(database)))

        val outcome = (repository.seed(seedRequest(DevSeedPreset.EDGE)) as AppResult.Success).data

        assertFalse("a failed hydration must be reported, not hidden", outcome.catalogHydrated)
        assertEquals(
            "the library write is its own transaction and must survive",
            DevSeedPreset.EDGE.defaultCount,
            database.libraryDao().getAllLibraryEntries().size,
        )
        assertNull(database.gameDetailsDao().getGameDetails(syntheticId(DevSeedPreset.EDGE, 0)))
    }

    @Test
    fun wipeLibrary_clearsEntriesAndKeepsCatalogRows() = runTest {
        val repository = repository()
        repository.seed(seedRequest(DevSeedPreset.EDGE))
        val seededGameId = syntheticId(DevSeedPreset.EDGE, 0)

        val outcome = (repository.wipe(DevWipeTarget.LIBRARY) as AppResult.Success).data

        assertEquals(0, outcome.libraryEntries)
        assertTrue(database.libraryDao().getAllLibraryEntries().isEmpty())
        assertNotNull("the catalog cache must survive a library wipe", database.gameDao().getGameById(seededGameId))
        assertNotNull(database.gameDetailsDao().getGameDetails(seededGameId))
    }

    @Test
    fun wipeSearchHistory_clearsHistoryAndCachedQueries() = runTest {
        database.gameDao().upsertGame(localGameEntity(CACHED_GAME_ID))
        database.searchDao().upsertSearchQuery(SearchQueryEntity("doom", 0L, 0L, 1))
        database.searchDao().insertSearchResults(listOf(SearchResultCrossRef("doom", CACHED_GAME_ID, 0)))
        database.searchHistoryDao().upsertSearchHistory(SearchHistoryEntity("doom", "Doom", 0L))
        assertEquals(1, database.searchHistoryDao().countSearchHistory())

        val outcome = (repository().wipe(DevWipeTarget.SEARCH_HISTORY) as AppResult.Success).data

        assertEquals(0, outcome.searchHistoryEntries)
        assertTrue(database.searchHistoryDao().observeRecentSearchQueries(limit = 10).first().isEmpty())
        assertTrue(database.searchDao().getRecentSearchQueriesFlow().first().isEmpty())
    }

    @Test
    fun wipeNotificationLedger_clearsEvents() = runTest {
        database.gameDao().upsertGame(localGameEntity(CACHED_GAME_ID))
        database.notificationEventDao().upsertEvent(
            NotificationEventEntity(
                eventKey = "release:$CACHED_GAME_ID",
                gameId = CACHED_GAME_ID,
                eventType = "RELEASE_TODAY",
                releaseDateEpochSeconds = 0L,
                notifiedAtEpochSeconds = 0L,
            ),
        )
        assertEquals(1, database.notificationEventDao().countEvents())

        val outcome = (repository().wipe(DevWipeTarget.NOTIFICATION_LEDGER) as AppResult.Success).data

        assertEquals(0, outcome.notificationEvents)
        assertFalse(database.notificationEventDao().hasEvent("release:$CACHED_GAME_ID"))
    }

    @Test
    fun wipeAll_resetsTheDatabasePreferencesAndTheBffOverride() = runTest {
        val repository = repository()
        repository.seed(seedRequest(DevSeedPreset.EDGE))
        preferences.updateData { it.toMutablePreferences().apply { set(PREFERENCE_KEY, true) } }
        bffUrlStore.setUrl(OVERRIDE_ORIGIN)
        assertEquals(OVERRIDE_ORIGIN, bffUrlStore.currentUrl().toString())

        val outcome = (repository.wipe(DevWipeTarget.ALL) as AppResult.Success).data

        assertEquals(0, outcome.libraryEntries)
        assertEquals(0, outcome.notificationEvents)
        assertEquals(0, outcome.searchHistoryEntries)
        assertTrue(database.libraryDao().getAllLibraryEntries().isEmpty())
        assertTrue(
            "preferences must not survive a full reset",
            preferences.data.first().asMap().isEmpty(),
        )
        assertNull("the debug BFF override must not survive a full reset", bffUrlStore.currentUrl())
    }

    @Test
    fun diagnostics_reportsTheStateTheAppIsIn() = runTest {
        val repository = repository()
        repository.seed(seedRequest(DevSeedPreset.EDGE))

        val diagnostics = (repository.diagnostics() as AppResult.Success).data

        assertEquals(DevSeedPreset.EDGE.defaultCount, diagnostics.libraryEntries)
        assertEquals(DevSeedPreset.EDGE.defaultCount, diagnostics.libraryEntriesByStatus.values.sum())
        assertTrue(diagnostics.roomSchemaVersion > 0)
        assertTrue(diagnostics.applicationId.isNotBlank())
        assertTrue(diagnostics.toClipboardText().contains("library: ${DevSeedPreset.EDGE.defaultCount}"))
    }

    private suspend fun insertLocalLibraryEntry(gameId: Long) {
        database.gameDao().upsertGame(localGameEntity(gameId))
        database.libraryDao().upsertLibraryEntry(
            LibraryEntryEntity(
                gameId = gameId,
                status = LibraryStatus.PLAYING,
                addedAtEpochSeconds = 1L,
                updatedAtEpochSeconds = 1L,
            ),
        )
    }

    private fun localGameEntity(id: Long) = GameEntity(
        id = id,
        name = "Local game $id",
        coverUrl = null,
        rating = null,
        releaseDateEpochSeconds = null,
        summary = null,
        genres = emptyList(),
        platforms = emptyList(),
        cachedAtEpochSeconds = 0L,
    )

    private companion object {
        const val LOCAL_GAME_ID = 500L
        const val CACHED_GAME_ID = 900L
        const val TEN_DAYS_SECONDS = 10 * 24 * 3_600L
        const val OVERRIDE_ORIGIN = "http://10.0.2.2:8080/"
        val PREFERENCE_KEY = booleanPreferencesKey("dev_tools_test")
    }
}

/** Fails the second transaction only: the library import succeeds, the catalog hydration does not. */
private class FailAfterFirstTransaction(private val delegate: TransactionRunner) : TransactionRunner {
    private var calls = 0

    override suspend fun <T> invoke(block: suspend () -> T): T {
        calls++
        check(calls == 1) { "catalog hydration is unavailable in this test" }
        return delegate.invoke(block)
    }
}

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** Paginated like the real source, and unable to resolve reserved ids — exactly the demo/live case. */
private class FakeCatalogSource : BffRemoteDataSource {
    override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> =
        CATALOG.drop(offset).take(limit)

    override suspend fun getTrendingGames(limit: Int, offset: Int): List<GameDto> = emptyList()

    override suspend fun getGameDetails(id: Long): GameDetailsDto =
        throw NoSuchElementException("reserved id $id is not in the catalog")

    override suspend fun searchGames(
        query: String?,
        genres: List<String>,
        platforms: List<String>,
        minRating: Int?,
        minYear: Int?,
        maxYear: Int?,
        sort: String?,
        limit: Int,
        offset: Int,
    ): List<GameDto> = emptyList()

    override suspend fun getRecommendationCandidates(
        genres: List<String>,
        themes: List<String>,
        platforms: List<String>,
        exclude: Set<Long>,
        similarTo: List<Long>,
        limit: Int,
    ): List<RecommendationCandidateDto> = emptyList()

    private companion object {
        val CATALOG: List<GameDto> = List(40) { index ->
            GameDto(
                id = 1_000L + index,
                name = "Catalog game $index",
                coverUrl = null,
                rating = null,
                releaseDateEpochSeconds = null,
                summary = null,
            )
        }
    }
}
