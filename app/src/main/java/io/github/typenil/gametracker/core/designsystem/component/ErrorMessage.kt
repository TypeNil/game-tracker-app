package io.github.typenil.gametracker.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.model.AppError

private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_TOO_MANY_REQUESTS = 429
private const val HTTP_STATUS_SERVER_ERROR_MIN = 500
private const val HTTP_STATUS_SERVER_ERROR_MAX = 599

/**
 * Maps [AppError] variants into user-facing localized string resources.
 */
@Composable
fun AppError.errorMessage(): String {
    return when (this) {
        is AppError.NetworkError -> stringResource(R.string.error_network)
        is AppError.HttpError -> when (statusCode) {
            HTTP_STATUS_NOT_FOUND -> stringResource(R.string.error_not_found)
            HTTP_STATUS_TOO_MANY_REQUESTS -> stringResource(R.string.error_rate_limit)
            in HTTP_STATUS_SERVER_ERROR_MIN..HTTP_STATUS_SERVER_ERROR_MAX -> stringResource(R.string.error_server)
            else -> stringResource(R.string.error_generic)
        }
        is AppError.SerializationError -> stringResource(R.string.error_serialization)
        is AppError.UnknownError -> stringResource(R.string.error_unknown)
    }
}
