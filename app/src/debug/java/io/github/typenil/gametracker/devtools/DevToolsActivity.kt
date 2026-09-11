package io.github.typenil.gametracker.devtools

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.typenil.gametracker.core.data.repository.UserPreferencesRepository
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.model.UserPreferences
import io.github.typenil.gametracker.devtools.ui.DevToolsRoute
import io.github.typenil.gametracker.devtools.ui.DevToolsViewModel
import javax.inject.Inject

/**
 * Hosts the developer tools screen and is the `gamertracker://dev/...` automation entry point, so a
 * tester tapping a button and a scripted `adb` command run exactly the same operations.
 *
 * Declared only in `src/debug/AndroidManifest.xml`: no release variant compiles this file.
 */
@AndroidEntryPoint
class DevToolsActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val viewModel: DevToolsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A configuration change or a process-death recreation replays the original Intent. Running
        // the command again would clear whatever the developer just built up, so only a genuinely
        // new launch runs it.
        if (savedInstanceState == null) {
            runCommandFrom(intent)
        }
        setContent {
            val preferences by userPreferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences(),
            )
            GameTrackerTheme(
                darkTheme = preferences.themeMode.isDark(isSystemInDarkTheme()),
                dynamicColor = preferences.dynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DevToolsRoute(viewModel = viewModel, onBackClick = ::finish)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The activity is `singleTop`, so a second adb command while this screen is on top has to be
        // honoured here rather than in onCreate.
        runCommandFrom(intent)
    }

    private fun runCommandFrom(intent: Intent?) {
        val uri = intent?.data ?: return
        val result = DevToolsCommandParser.parse(uri.host, uri.pathSegments, uri::getQueryParameter)
        when (result) {
            is DevCommandParseResult.Command -> viewModel.run(result.command)
            is DevCommandParseResult.Invalid -> viewModel.onCommandRejected(result.reason)
            DevCommandParseResult.NotADevCommand -> Unit
        }
    }
}
