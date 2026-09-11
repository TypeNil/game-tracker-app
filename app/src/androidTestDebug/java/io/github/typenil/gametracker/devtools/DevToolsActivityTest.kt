package io.github.typenil.gametracker.devtools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.database.GameTrackerDatabase
import io.github.typenil.gametracker.core.database.entity.GameEntity
import io.github.typenil.gametracker.core.database.entity.LibraryEntryEntity
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the `gamertracker://dev/...` entry point through a real launch.
 *
 * The observable is the app's own database, because that is the effect a tester checks. `LIBRARY` is
 * the target used here rather than `ALL`: a full reset deliberately relaunches the task, which would
 * finish the very Activity under test, and that decision is covered by `DevToolsViewModelTest`.
 *
 * This test therefore mutates the app under test's real library: it is an instrumentation test for a
 * scratch device, and it clears the library in `setUp` before it asserts anything.
 */
@RunWith(AndroidJUnit4::class)
class DevToolsActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * A second Room instance over the app's real database file. The command under test runs in the
     * app's own instance; this one only ever reads the committed result back.
     */
    private lateinit var database: GameTrackerDatabase

    @Before
    fun setUp() {
        database = Room.databaseBuilder(
            context,
            GameTrackerDatabase::class.java,
            GameTrackerDatabase.DATABASE_NAME,
        ).build()
        runBlocking {
            database.libraryDao().deleteAllLibraryEntries()
            database.gameDao().upsertGame(LOCAL_GAME)
            database.libraryDao().upsertLibraryEntry(LOCAL_ENTRY)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun wipeLibraryCommand_clearsTheLibraryThroughTheRealDatabase() {
        launch("dev/wipe?target=LIBRARY")

        awaitLibraryEmptied()
    }

    @Test
    fun recreation_doesNotReplayTheCommand() {
        ActivityScenario.launch<DevToolsActivity>(deepLink(COMMAND_PATH)).use { scenario ->
            awaitLibraryEmptied()

            // Stand in for anything the developer builds up after the command ran: a replayed Intent
            // would delete it again.
            runBlocking { database.libraryDao().upsertLibraryEntry(LOCAL_ENTRY) }

            scenario.recreate()
            scenario.onActivity { }
        }

        assertTrue("a configuration change must not re-run the command", hasLocalEntry())
    }

    @Test
    fun unknownCommand_isReportedInsteadOfRunning() {
        ActivityScenario.launch<DevToolsActivity>(deepLink("dev/nonsense")).use { scenario ->
            scenario.onActivity { }
        }

        assertTrue("an unparsable command must not touch data", hasLocalEntry())
    }

    @Test
    fun launchWithoutAUri_runsNothing() {
        val intent = Intent(context, DevToolsActivity::class.java)

        ActivityScenario.launch<DevToolsActivity>(intent).use { scenario ->
            scenario.onActivity { }
        }

        assertTrue("opening the screen must not run a command", hasLocalEntry())
    }

    private fun launch(path: String) {
        ActivityScenario.launch<DevToolsActivity>(deepLink(path)).use { scenario ->
            scenario.onActivity { }
        }
    }

    private fun deepLink(path: String) = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("gamertracker://$path"),
        context,
        DevToolsActivity::class.java,
    )

    private fun hasLocalEntry(): Boolean =
        runBlocking { database.libraryDao().getLibraryEntry(LOCAL_GAME_ID) } != null

    /** The command runs off the main thread, so the wipe has to be awaited rather than assumed. */
    private fun awaitLibraryEmptied() {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (!hasLocalEntry()) return
            Thread.sleep(POLL_MILLIS)
        }
        assertFalse("the library wipe never reached the database", hasLocalEntry())
    }

    private companion object {
        const val COMMAND_PATH = "dev/wipe?target=LIBRARY"
        const val LOCAL_GAME_ID = 4_242L
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L

        val LOCAL_GAME = GameEntity(
            id = LOCAL_GAME_ID,
            name = "Developer tools test entry",
            coverUrl = null,
            rating = null,
            releaseDateEpochSeconds = null,
            summary = null,
            genres = emptyList(),
            platforms = emptyList(),
            cachedAtEpochSeconds = 0L,
        )

        val LOCAL_ENTRY = LibraryEntryEntity(
            gameId = LOCAL_GAME_ID,
            status = LibraryStatus.PLAYING,
            addedAtEpochSeconds = 1L,
            updatedAtEpochSeconds = 1L,
        )
    }
}
