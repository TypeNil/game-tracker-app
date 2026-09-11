package io.github.typenil.gametracker.devtools

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Debug-only bindings for the developer tools. Nothing here is `@Singleton`: the restarter holds
 * only the application context, which is a singleton already.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DevToolsModule {

    @Binds
    abstract fun bindDevAppRestarter(impl: IntentDevAppRestarter): DevAppRestarter
}
