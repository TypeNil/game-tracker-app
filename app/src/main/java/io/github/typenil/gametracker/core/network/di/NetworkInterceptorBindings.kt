package io.github.typenil.gametracker.core.network.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Interceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkInterceptorBindings {

    @Multibinds
    abstract fun networkInterceptors(): Set<Interceptor>
}
