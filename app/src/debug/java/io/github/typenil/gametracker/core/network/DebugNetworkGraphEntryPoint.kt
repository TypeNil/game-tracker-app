package io.github.typenil.gametracker.core.network

import coil3.ImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.typenil.gametracker.core.network.di.ImageHttpClient
import io.github.typenil.gametracker.core.network.di.TransportHttpClient
import okhttp3.OkHttpClient

/**
 * Debug-only Hilt entry point exposing the production network graph to
 * instrumentation tests. Lets [NetworkGraphTest] assert the clients and
 * loader actually used at runtime instead of rebuilt copies.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugNetworkGraphEntryPoint {
    @TransportHttpClient
    fun transportClient(): OkHttpClient

    fun apiClient(): OkHttpClient
    @ImageHttpClient
    fun imageClient(): OkHttpClient

    fun imageLoader(): ImageLoader

    fun debugBffUrlStore(): DebugBffUrlStore
}
