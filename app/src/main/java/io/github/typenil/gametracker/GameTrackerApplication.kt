package io.github.typenil.gametracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.typenil.gametracker.core.notification.ReleaseNotifier
import io.github.typenil.gametracker.core.work.ReleaseNotificationScheduler
import javax.inject.Inject

/**
 * Root Application class triggering Hilt code generation and dependency container initialization.
 */
@HiltAndroidApp
class GameTrackerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var releaseNotifier: ReleaseNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        releaseNotifier.createNotificationChannels()
        ReleaseNotificationScheduler.schedulePeriodicCheck(this)
    }
}
