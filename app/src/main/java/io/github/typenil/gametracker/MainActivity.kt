package io.github.typenil.gametracker

import android.content.Intent
import android.os.Bundle
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.core.connectivity.NetworkMonitor
import io.github.typenil.gametracker.core.model.NotificationEventType
import io.github.typenil.gametracker.core.model.ReleaseEvent
import io.github.typenil.gametracker.core.notification.ReleaseNotifier
import io.github.typenil.gametracker.navigation.AppNavHost
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var releaseNotifier: ReleaseNotifier
    override fun onCreate(savedInstanceState: Bundle?) {
        Trace.beginSection(TRACE_MAIN_ACTIVITY_ON_CREATE)
        try {
            installSplashScreen()
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            handleTestNotification(intent)
            setContent {
                GameTrackerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavHost(networkMonitor = networkMonitor)
                    }
                }
            }
        } finally {
            Trace.endSection()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTestNotification(intent)
    }

    private fun handleTestNotification(intent: Intent?) {
        if (intent?.action == ACTION_TEST_NOTIFICATION) {
            val gameId = intent.getLongExtra(EXTRA_GAME_ID, DEFAULT_TEST_GAME_ID)
            val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: DEFAULT_TEST_GAME_NAME
            releaseNotifier.postReleaseNotification(
                ReleaseEvent(
                    gameId = gameId,
                    gameName = gameName,
                    eventType = NotificationEventType.RELEASE_TODAY,
                    releaseDateEpochSeconds = System.currentTimeMillis() / 1000
                )
            )
        }
    }

    companion object {
        const val ACTION_TEST_NOTIFICATION = "io.github.typenil.gametracker.ACTION_TEST_NOTIFICATION"
        const val EXTRA_GAME_ID = "gameId"
        const val EXTRA_GAME_NAME = "gameName"
        private const val DEFAULT_TEST_GAME_ID = 1942L
        private const val DEFAULT_TEST_GAME_NAME = "The Witcher 3: Wild Hunt"
    }
}

private const val TRACE_MAIN_ACTIVITY_ON_CREATE = "GameTracker.MainActivity.onCreate"
