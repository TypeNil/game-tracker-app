package io.github.typenil.gametracker.core.data.recommendations

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Demo-flavor bindings for the first-run seeding decision. Nothing here is `@Singleton`: both
 * implementations are stateless, and the shared state they mediate (`DataStore`, `PackageManager`)
 * is owned elsewhere.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DemoSeedModule {

    @Binds
    abstract fun bindDemoSeedMarker(impl: DataStoreDemoSeedMarker): DemoSeedMarker

    @Binds
    abstract fun bindInstallEra(impl: PackageManagerInstallEra): InstallEra
}
