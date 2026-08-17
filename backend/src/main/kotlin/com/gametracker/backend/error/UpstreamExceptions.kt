package com.gametracker.backend.error

/**
 * Иерархия исключений для взаимодействия с внешними upstream-сервисами (Twitch OAuth2 и IGDB API).
 */
sealed class UpstreamException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Превышена квота или лимит запросов upstream API (HTTP 429).
 * Содержит гарантированно валидированное значение [retryAfterSeconds] для передачи клиенту.
 */
class UpstreamRateLimitException(
    val retryAfterSeconds: Long
) : UpstreamException("Upstream rate limit reached. Retry after $retryAfterSeconds seconds.")

/**
 * Ошибка шлюза upstream: некорректный формат ответа, ошибка сериализации или upstream 502/4xx (HTTP 502).
 */
class UpstreamBadGatewayException(
    message: String,
    cause: Throwable? = null
) : UpstreamException(message, cause)

/**
 * Upstream-сервис временно недоступен или произошел сбой сетевого подключения (HTTP 503).
 */
class UpstreamServiceUnavailableException(
    message: String,
    cause: Throwable? = null
) : UpstreamException(message, cause)

/**
 * Истек таймаут ожидания соединения или ответа от upstream-сервиса (HTTP 504).
 */
class UpstreamTimeoutException(
    message: String,
    cause: Throwable? = null
) : UpstreamException(message, cause)
