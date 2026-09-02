package io.github.typenil.gametracker.core.model

/**
 * A typed carrier that lets the data layer deliver an already-classified [AppError] through APIs
 * that only accept a [Throwable] (e.g. `RemoteMediator.MediatorResult.Error`) without re-running
 * transport-specific mapping in the presentation layer. Stack traces are disabled: the original
 * failure is preserved as [cause].
 */
class AppErrorException(
    val error: AppError,
    cause: Throwable,
) : RuntimeException(null, cause, false, false)
