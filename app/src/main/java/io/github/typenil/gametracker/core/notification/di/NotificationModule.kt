package io.github.typenil.gametracker.core.notification.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.notification.ReleaseNotifier
import io.github.typenil.gametracker.core.notification.SystemReleaseNotifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindReleaseNotifier(
        impl: SystemReleaseNotifier
    ): ReleaseNotifier
}
