package io.github.typenil.gametracker.navigation

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import io.github.typenil.gametracker.core.data.repository.GameRepository
import io.github.typenil.gametracker.core.data.repository.LibraryRepository
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.model.AppResult
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Owns the persistent state required by navigation tests.
 *
 * Instrumentation classes share the installed application's Room and DataStore files, and their
 * execution order is not a fixture contract. The developer-tools tests deliberately clear the
 * library, while the demo seeder deliberately refuses to repopulate a resolved install. Resetting
 * the exact catalog/library slice here keeps navigation tests independent without weakening that
 * production behavior.
 */
internal object DemoNavigationFixture {

    fun install(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugNavigationFixtureEntryPoint::class.java,
        )

        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint.database().clearAllTables()
            }
            requireSuccess(
                entryPoint.userPreferencesRepository().clearRecommendationPreferences(),
                "clear recommendation preferences",
            )

            requiredGameIds.forEach { gameId ->
                requireSuccess(
                    entryPoint.gameRepository().refreshGameDetails(gameId, force = true),
                    "hydrate game $gameId",
                )
            }

            val now = System.currentTimeMillis() / 1_000L
            starterLibraryEntries(now).forEach { entry ->
                requireSuccess(
                    entryPoint.libraryRepository().saveLibraryEntry(entry),
                    "seed library game ${entry.gameId}",
                )
            }
        }
    }

    private fun requireSuccess(result: AppResult<Unit>, operation: String) {
        if (result is AppResult.Error) {
            throw AssertionError("$operation failed: $result")
        }
    }


    private val requiredGameIds = longArrayOf(
        WITCHER_ID,
        ELDEN_RING_ID,
        RED_DEAD_REDEMPTION_2_ID,
        GTA_VI_ID,
        SPACE_INVADERS_ID,
        DOOM_ID,
        MYSTERY_INDIE_ID,
        ARENA_PRO_ID,
    )

    private fun starterLibraryEntries(now: Long): List<LibraryEntry> = listOf(
        LibraryEntry(
            gameId = WITCHER_ID,
            status = LibraryStatus.COMPLETED,
            userRating = 10,
            hoursPlayed = 150,
            isFavorite = true,
            addedAtEpochSeconds = now - 30 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - 5 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = MYSTERY_INDIE_ID,
            status = LibraryStatus.PLAYING,
            hoursPlayed = 12,
            addedAtEpochSeconds = now - 10 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = GTA_VI_ID,
            status = LibraryStatus.WISHLIST,
            isFavorite = true,
            releaseNotificationsEnabled = true,
            addedAtEpochSeconds = now - 20 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - 20 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = DOOM_ID,
            status = LibraryStatus.COMPLETED,
            userRating = 9,
            hoursPlayed = 25,
            addedAtEpochSeconds = now - 40 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - 15 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = ARENA_PRO_ID,
            status = LibraryStatus.DROPPED,
            userRating = 4,
            hoursPlayed = 8,
            addedAtEpochSeconds = now - 15 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - 2 * SECONDS_PER_DAY,
        ),
        LibraryEntry(
            gameId = SPACE_INVADERS_ID,
            status = LibraryStatus.NOT_INTERESTED,
            addedAtEpochSeconds = now - 50 * SECONDS_PER_DAY,
            updatedAtEpochSeconds = now - 50 * SECONDS_PER_DAY,
        ),
    )

    private const val SECONDS_PER_DAY = 86_400L

    private const val WITCHER_ID = 1_942L
    private const val ELDEN_RING_ID = 119_133L
    private const val RED_DEAD_REDEMPTION_2_ID = 25_076L
    private const val GTA_VI_ID = 900_003L
    private const val SPACE_INVADERS_ID = 900_004L
    private const val DOOM_ID = 900_005L
    private const val MYSTERY_INDIE_ID = 900_009L
    private const val ARENA_PRO_ID = 900_012L
}
