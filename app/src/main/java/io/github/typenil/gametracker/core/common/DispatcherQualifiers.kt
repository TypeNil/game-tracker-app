package io.github.typenil.gametracker.core.common

import javax.inject.Qualifier

/**
 * Qualifier for injection of [kotlinx.coroutines.Dispatchers.IO] into repositories and data sources.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifier for injection of [kotlinx.coroutines.Dispatchers.Default] for CPU-bound computations.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Qualifier for injection of [kotlinx.coroutines.Dispatchers.Main] for UI-bound operations.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
