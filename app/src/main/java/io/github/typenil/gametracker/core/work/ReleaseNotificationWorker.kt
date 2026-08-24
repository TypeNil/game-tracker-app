package io.github.typenil.gametracker.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.typenil.gametracker.core.common.IoDispatcher
import io.github.typenil.gametracker.core.data.notification.ReleaseEventDetector
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.database.dao.GameDao
import io.github.typenil.gametracker.core.database.dao.GameDetailsDao
import io.github.typenil.gametracker.core.database.dao.LibraryDao
import io.github.typenil.gametracker.core.database.dao.NotificationEventDao
import io.github.typenil.gametracker.core.database.entity.NotificationEventEntity
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryStatus
import io.github.typenil.gametracker.core.model.ReleaseEvent
import io.github.typenil.gametracker.core.notification.ReleaseNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Background worker checking release dates for user-tracked library games and posting local notifications.
 */
@HiltWorker
class ReleaseNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val gameDetailsDao: GameDetailsDao,
    private val notificationEventDao: NotificationEventDao,
    private val gameRepository: GameRepository,
    private val releaseNotifier: ReleaseNotifier,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val allEntries = libraryDao.getAllLibraryEntries()
        val trackedEntries = allEntries.filter { isTrackedStatus(it.status) }
        if (trackedEntries.isEmpty()) return@withContext Result.success()

        val nowEpochSeconds = Instant.now().epochSecond
        val retentionThreshold = nowEpochSeconds - RETENTION_DAYS * SECONDS_PER_DAY
        notificationEventDao.deleteOldEvents(retentionThreshold)

        var hasRetryableError = false

        trackedEntries.chunked(BATCH_SIZE).forEach { batch ->
            for (entry in batch) {
                val isRetryable = processTrackedEntry(entry.gameId, nowEpochSeconds)
                if (isRetryable) {
                    hasRetryableError = true
                }
            }
        }

        when {
            hasRetryableError && runAttemptCount < MAX_RETRIES -> Result.retry()
            hasRetryableError -> Result.failure()
            else -> Result.success()
        }
    }

    private suspend fun processTrackedEntry(gameId: Long, nowEpochSeconds: Long): Boolean {
        val previousDetails = gameDetailsDao.getGameDetails(gameId)
        val previousDate = previousDetails?.releaseDateEpochSeconds
            ?: gameDao.getGameById(gameId)?.releaseDateEpochSeconds

        var retryableError = false
        when (val refreshResult = gameRepository.refreshGameDetails(gameId, force = true)) {
            is AppResult.Success -> {
                // Room SSOT updated
            }
            is AppResult.Error -> {
                if (!isNonRetryableClientError(refreshResult.error)) {
                    retryableError = true
                }
            }
        }

        val currentDetails = gameDetailsDao.getGameDetails(gameId)
        val currentDate = currentDetails?.releaseDateEpochSeconds
            ?: gameDao.getGameById(gameId)?.releaseDateEpochSeconds
        val gameName = currentDetails?.name
            ?: gameDao.getGameById(gameId)?.name
            ?: "Game #$gameId"

        val events = ReleaseEventDetector.detectEvents(
            nowEpochSeconds = nowEpochSeconds,
            gameId = gameId,
            gameName = gameName,
            previousReleaseDate = previousDate,
            currentReleaseDate = currentDate
        )

        dispatchAndRecordEvents(events, nowEpochSeconds)
        return retryableError
    }

    private suspend fun dispatchAndRecordEvents(
        events: List<ReleaseEvent>,
        nowEpochSeconds: Long
    ) {
        for (event in events) {
            if (!notificationEventDao.hasEvent(event.eventKey)) {
                val notified = releaseNotifier.postReleaseNotification(event)
                if (notified) {
                    notificationEventDao.upsertEvent(
                        NotificationEventEntity(
                            eventKey = event.eventKey,
                            gameId = event.gameId,
                            eventType = event.eventType.name,
                            releaseDateEpochSeconds = event.releaseDateEpochSeconds,
                            notifiedAtEpochSeconds = nowEpochSeconds
                        )
                    )
                }
            }
        }
    }

    private fun isTrackedStatus(status: LibraryStatus): Boolean {
        return status == LibraryStatus.WISHLIST ||
            status == LibraryStatus.PLAYING ||
            status == LibraryStatus.COMPLETED
    }

    private fun isNonRetryableClientError(error: AppError): Boolean {
        return error is AppError.HttpError && error.statusCode in HTTP_CLIENT_ERROR_RANGE
    }

    companion object {
        const val MAX_RETRIES = 3
        const val RETENTION_DAYS = 90L
        const val SECONDS_PER_DAY = 86_400L
        const val BATCH_SIZE = 20
        private val HTTP_CLIENT_ERROR_RANGE = 400..499
    }
}
