package io.github.typenil.gametracker.core.model

/**
 * Functional result type representing either a successful computation [Success] or a typed failure [Error].
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Error(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error
}

/**
 * Returns the encapsulated value if this instance represents [AppResult.Success] or null if it is [AppResult.Error].
 */
fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> data
    is AppResult.Error -> null
}

/**
 * Returns the encapsulated value if this instance represents [AppResult.Success] or [default] if it is [AppResult.Error].
 */
inline fun <T> AppResult<T>.getOrElse(default: (AppError) -> T): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Error -> default(error)
}

/**
 * Transforms the successful value using [transform] if this instance represents [AppResult.Success].
 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
}

/**
 * Performs [action] on the encapsulated value if this instance is [AppResult.Success].
 */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) {
        action(data)
    }
    return this
}

/**
 * Performs [action] on the encapsulated error if this instance is [AppResult.Error].
 */
inline fun <T> AppResult<T>.onError(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Error) {
        action(error)
    }
    return this
}
