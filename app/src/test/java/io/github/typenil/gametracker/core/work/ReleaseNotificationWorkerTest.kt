package io.github.typenil.gametracker.core.work

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.NotificationEventDao
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.notification.ReleaseNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ReleaseNotificationWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val libraryDao: LibraryDao = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val gameDetailsDao: GameDetailsDao = mockk(relaxed = true)
    private val notificationEventDao: NotificationEventDao = mockk(relaxed = true)
    private val gameRepository: GameRepository = mockk(relaxed = true)
    private val releaseNotifier: ReleaseNotifier = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var worker: ReleaseNotificationWorker

    @Before
    fun setup() {
        every { workerParams.runAttemptCount } returns 0
        coEvery { notificationEventDao.deleteOldEvents(any()) } returns 0
        coEvery { notificationEventDao.hasEvent(any()) } returns false
        coEvery { notificationEventDao.upsertEvent(any()) } returns 1L
        every { releaseNotifier.postReleaseNotification(any()) } returns true

        worker = ReleaseNotificationWorker(
            appContext = context,
            workerParams = workerParams,
            libraryDao = libraryDao,
            gameDao = gameDao,
            gameDetailsDao = gameDetailsDao,
            notificationEventDao = notificationEventDao,
            gameRepository = gameRepository,
            releaseNotifier = releaseNotifier,
            ioDispatcher = testDispatcher
        )
    }

    private fun createLibraryEntry(gameId: Long, status: LibraryStatus): LibraryEntryEntity {
        return LibraryEntryEntity(
            gameId = gameId,
            status = status,
            addedAtEpochSeconds = 1000L,
            updatedAtEpochSeconds = 1000L
        )
    }

    private fun createGame(id: Long, releaseEpoch: Long?): GameEntity {
        return GameEntity(
            id = id,
            name = "Test Game $id",
            coverUrl = null,
            rating = 90.0,
            releaseDateEpochSeconds = releaseEpoch,
            summary = "Summary",
            genres = emptyList(),
            platforms = listOf("PC"),
            cachedAtEpochSeconds = 1000L
        )
    }

    @Test
    fun doWork_whenLibraryIsEmpty_returnsSuccessAndDoesNotFetch_butCleansUpOldEvents() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns emptyList()

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { gameRepository.refreshGameDetails(any(), any()) }
        coVerify(exactly = 1) { notificationEventDao.deleteOldEvents(any()) }
    }
    @Test
    fun doWork_filtersOutDroppedAndNotInterestedStatuses() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(1L, LibraryStatus.DROPPED),
            createLibraryEntry(2L, LibraryStatus.NOT_INTERESTED)
        )

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { gameRepository.refreshGameDetails(any(), any()) }
        coVerify(exactly = 0) { releaseNotifier.postReleaseNotification(any()) }
    }

    @Test
    fun doWork_whenTrackedGameReleasesToday_notifiesAndRecordsEvent() = runTest(testDispatcher) {
        val todayEpoch = Instant.now().epochSecond
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, todayEpoch)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Success(Unit)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { releaseNotifier.postReleaseNotification(any()) }
        coVerify(exactly = 1) { notificationEventDao.upsertEvent(any()) }
    }

    @Test
    fun doWork_whenEventAlreadyRecorded_doesNotNotifyAgain() = runTest(testDispatcher) {
        val todayEpoch = Instant.now().epochSecond
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, todayEpoch)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Success(Unit)
        // Event already exists in Room
        coEvery { notificationEventDao.hasEvent(any()) } returns true

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { releaseNotifier.postReleaseNotification(any()) }
        coVerify(exactly = 0) { notificationEventDao.upsertEvent(any()) }
    }

    @Test
    fun doWork_whenHttp4xxClientErrorOccurs_doesNotRetry() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, null)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Error(
            AppError.HttpError(statusCode = 404, errorCode = "NOT_FOUND")
        )

        val result = worker.doWork()

        // 4xx is a client error, should not retry
        assertEquals(Result.success(), result)
    }

    @Test
    fun doWork_whenSerializationErrorOccurs_doesNotRetry() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, null)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Error(
            AppError.SerializationError("Malformed payload")
        )

        val result = worker.doWork()

        // SerializationError is deterministic, should not trigger retry
        assertEquals(Result.success(), result)
    }

    @Test
    fun doWork_whenNetworkErrorOccurs_retriesUnderMaxAttemptLimit() = runTest(testDispatcher) {
        every { workerParams.runAttemptCount } returns 1
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, null)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Error(
            AppError.NetworkError
        )

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun doWork_whenNetworkErrorOccurs_failsWhenMaxAttemptsExceeded() = runTest(testDispatcher) {
        every { workerParams.runAttemptCount } returns 3
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, null)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Error(
            AppError.NetworkError
        )

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun doWork_whenHttp5xxServerErrorOccurs_retriesUnderMaxAttemptLimit() = runTest(testDispatcher) {
        every { workerParams.runAttemptCount } returns 1
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, null)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Error(
            AppError.HttpError(statusCode = 503, errorCode = "UPSTREAM")
        )

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun doWork_whenNotificationPermissionDenied_doesNotCrashAndDoesNotRecordEvent() = runTest(testDispatcher) {
        val todayEpoch = Instant.now().epochSecond
        every { releaseNotifier.postReleaseNotification(any()) } returns false
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, todayEpoch)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Success(Unit)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { releaseNotifier.postReleaseNotification(any()) }
        coVerify(exactly = 0) { notificationEventDao.upsertEvent(any()) }
    }

    @Test
    fun doWork_cleansUpOldNotificationEvents() = runTest(testDispatcher) {
        coEvery { libraryDao.getAllLibraryEntries() } returns listOf(
            createLibraryEntry(10L, LibraryStatus.WISHLIST)
        )
        coEvery { gameDao.getGameById(10L) } returns createGame(10L, null)
        coEvery { gameDetailsDao.getGameDetails(10L) } returns null
        coEvery { gameRepository.refreshGameDetails(10L, force = true) } returns AppResult.Success(Unit)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { notificationEventDao.deleteOldEvents(any()) }
    }
}
