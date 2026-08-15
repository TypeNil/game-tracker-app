package io.github.typenil.gametracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Root Application class triggering Hilt code generation and dependency container initialization.
 */
@HiltAndroidApp
class GameTrackerApplication : Application()
