package io.github.typenil.gametracker.devtools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.network.DebugBffUrlStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the `gamertracker://dev/...` entry point through a real launch.
 *
 * The observable channel is `DebugBffUrlStore`, which the `ALL` target resets and which needs no
 * database access from the test. It is the same trick the command's own effect provides: a command
 * that ran clears the override, a command that did not run leaves it alone.
 */
@RunWith(AndroidJUnit4::class)
class DevToolsActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        store().setUrl(null as String?)
        store().setUrl(OVERRIDE_ORIGIN)
        assertEquals(OVERRIDE_ORIGIN, store().currentUrl().toString())
    }

    private fun store() = DebugBffUrlStore(context)

    private fun deepLink(path: String) =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("gamertracker://$path"),
            context,
            DevToolsActivity::class.java,
        )

    @Test
    fun wipeCommand_resetsTheDebugBffOverride() {
        ActivityScenario.launch<DevToolsActivity>(deepLink("dev/wipe?target=ALL")).use { scenario ->
            scenario.onActivity { }
        }

        assertNull("the ALL wipe must reach the repository", store().currentUrl())
    }

    @Test
    fun recreation_doesNotReplayTheCommand() {
        ActivityScenario.launch<DevToolsActivity>(deepLink("dev/wipe?target=ALL")).use { scenario ->
            scenario.onActivity { }
            // Stand in for anything the developer sets up after the command ran: a replayed Intent
            // would clear it again.
            store().setUrl(OVERRIDE_ORIGIN)

            scenario.recreate()
            scenario.onActivity { }
        }

        assertNotNull("a configuration change must not re-run the command", store().currentUrl())
    }

    @Test
    fun unknownCommand_isReportedInsteadOfRunning() {
        ActivityScenario.launch<DevToolsActivity>(deepLink("dev/nonsense")).use { scenario ->
            scenario.onActivity { }
        }

        assertEquals(OVERRIDE_ORIGIN, store().currentUrl().toString())
    }

    @Test
    fun launchWithoutAUri_runsNothing() {
        val intent = Intent(context, DevToolsActivity::class.java)

        ActivityScenario.launch<DevToolsActivity>(intent).use { scenario ->
            scenario.onActivity { }
        }

        assertEquals(OVERRIDE_ORIGIN, store().currentUrl().toString())
    }

    private companion object {
        const val OVERRIDE_ORIGIN = "http://10.0.2.2:8080/"
    }
}
