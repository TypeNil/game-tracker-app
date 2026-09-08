package io.github.typenil.gametracker

import android.app.Application
import android.os.Trace
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.github.typenil.gametracker.core.common.DefaultDispatcher
import io.github.typenil.gametracker.core.notification.ReleaseNotifier
import io.github.typenil.gametracker.core.work.ReleaseNotificationScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TRACE_APPLICATION_ON_CREATE = "GameTracker.Application.onCreate"

@HiltAndroidApp
class GameTrackerApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var releaseNotifier: ReleaseNotifier

    @Inject
    @field:DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher
    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>


    private val startScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + defaultDispatcher)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()


    override fun onCreate() {
        Trace.beginSection(TRACE_APPLICATION_ON_CREATE)
        try {
            super.onCreate()
            releaseNotifier.createNotificationChannels()
            startScope.launch {
                ReleaseNotificationScheduler.schedulePeriodicCheck(this@GameTrackerApplication)
            }
        } finally {
            Trace.endSection()
        }
    }
}
