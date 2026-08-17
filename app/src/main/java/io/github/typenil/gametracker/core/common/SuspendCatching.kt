package io.github.typenil.gametracker.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes the given suspending [block], returning a [Result] containing the result or any thrown [Exception],
 * while strictly rethrowing [CancellationException] to preserve structured concurrency and letting fatal JVM
 * [Error]s propagate.
 */
inline fun <T, R> T.runSuspendCatching(block: T.() -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Suspending top-level variant of [runSuspendCatching].
 */
inline fun <R> runSuspendCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
