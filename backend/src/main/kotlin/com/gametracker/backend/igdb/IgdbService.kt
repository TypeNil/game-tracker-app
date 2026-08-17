package com.gametracker.backend.igdb

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.error.UpstreamBadGatewayException
import com.gametracker.backend.error.UpstreamRateLimitException
import com.gametracker.backend.error.UpstreamServiceUnavailableException
import com.gametracker.backend.error.UpstreamTimeoutException
import com.gametracker.backend.models.IgdbGame
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.discardRemaining
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private const val DEFAULT_RETRY_DELAY_BASE_MS = 100L
private const val JITTER_MAX_MS = 50
private const val DEFAULT_RETRY_AFTER_SECONDS = 5L
private const val MAX_TRANSIENT_RETRIES = 2

class IgdbService(
    private val httpClient: HttpClient,
    private val tokenManager: IgdbTokenManager,
    private val config: IgdbConfig,
    private val rateLimiter: SmoothRateLimiter = SmoothRateLimiter(),
    private val concurrencySemaphore: Semaphore = Semaphore(4),
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger("IgdbService")

    suspend fun queryGames(apicalypseQuery: String): List<IgdbGame> {
        logger.info("Executing IGDB query (length={})", apicalypseQuery.length)
        val response = executeWithRetry(apicalypseQuery)
        return try {
            response.body<List<IgdbGame>>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.error("Failed to deserialize IGDB response", e)
            throw UpstreamBadGatewayException("Failed to deserialize IGDB game response", e)
        } finally {
            response.discardRemaining()
        }
    }

    private suspend fun executeWithRetry(apicalypseQuery: String): HttpResponse {
        var authRetried = false
        var transientRetries = 0

        while (true) {
            val token = tokenManager.getValidAccessToken()
            val responseResult = runCatching { sendRawQuery(token, apicalypseQuery) }

            if (responseResult.isFailure) {
                transientRetries = handleNetworkFailure(responseResult.exceptionOrNull()!!, transientRetries)
                continue
            }

            val response = responseResult.getOrThrow()

            if (response.status == HttpStatusCode.Unauthorized) {
                authRetried = handleAuthUnauthorized(response, token, authRetried)
                continue
            }

            if (isTransientStatus(response.status) && transientRetries < MAX_TRANSIENT_RETRIES) {
                transientRetries++
                response.discardRemaining()
                val backoffMs = DEFAULT_RETRY_DELAY_BASE_MS * transientRetries + Random.nextLong(0, JITTER_MAX_MS.toLong())
                logger.warn(
                    "IGDB returned transient status {} (attempt {}). Retrying in {}ms...",
                    response.status,
                    transientRetries,
                    backoffMs
                )
                delay(backoffMs)
                continue
            }

            if (!response.status.isSuccess()) {
                handleNonSuccessStatus(response)
            }

            return response
        }
    }

    private suspend fun sendRawQuery(token: String, apicalypseQuery: String): HttpResponse {
        return concurrencySemaphore.withPermit {
            rateLimiter.acquire()
            httpClient.post("https://api.igdb.com/v4/games") {
                header("Client-ID", config.clientId)
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
                setBody(apicalypseQuery)
            }
        }
    }

    private suspend fun handleAuthUnauthorized(response: HttpResponse, token: String, authRetried: Boolean): Boolean {
        response.discardRemaining()
        if (!authRetried) {
            logger.warn("Received 401 Unauthorized from IGDB. Invalidating token and retrying once.")
            tokenManager.invalidateToken(token)
            return true
        } else {
            logger.error("IGDB returned 401 Unauthorized even after token refresh. Invalidating refreshed token.")
            tokenManager.invalidateToken(token)
            throw UpstreamBadGatewayException("IGDB authentication failed after token refresh")
        }
    }

    private suspend fun handleNetworkFailure(exception: Throwable, currentRetries: Int): Int {
        if (exception is CancellationException) throw exception
        if (isTransientNetworkError(exception) && currentRetries < MAX_TRANSIENT_RETRIES) {
            val nextRetries = currentRetries + 1
            val backoffMs = DEFAULT_RETRY_DELAY_BASE_MS * nextRetries + Random.nextLong(0, JITTER_MAX_MS.toLong())
            logger.warn("Transient network error (attempt {}): {}. Retrying in {}ms...", nextRetries, exception.message, backoffMs)
            delay(backoffMs)
            return nextRetries
        }
        when (exception) {
            is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException ->
                throw UpstreamTimeoutException("IGDB request timed out", exception)
            is IOException ->
                throw UpstreamServiceUnavailableException("Failed to connect to IGDB API", exception)
            else ->
                throw UpstreamBadGatewayException("Unexpected error during IGDB request", exception)
        }
    }

    private suspend fun handleNonSuccessStatus(response: HttpResponse): Nothing {
        val status = response.status
        val retryAfterHeader = response.headers[HttpHeaders.RetryAfter]
        response.discardRemaining()

        logger.error("IGDB request failed with status {}", status)

        when (status) {
            HttpStatusCode.TooManyRequests -> {
                val retryAfterSeconds = parseRetryAfter(retryAfterHeader)
                throw UpstreamRateLimitException(retryAfterSeconds)
            }
            HttpStatusCode.BadGateway -> throw UpstreamBadGatewayException("IGDB returned 502 Bad Gateway")
            HttpStatusCode.ServiceUnavailable -> throw UpstreamServiceUnavailableException("IGDB returned 503 Service Unavailable")
            HttpStatusCode.GatewayTimeout -> throw UpstreamTimeoutException("IGDB returned 504 Gateway Timeout")
            else -> throw UpstreamBadGatewayException("IGDB request failed with status ${status.value}")
        }
    }

    private fun isTransientNetworkError(e: Throwable): Boolean {
        return e is HttpRequestTimeoutException || e is SocketTimeoutException || e is ConnectTimeoutException
    }

    private fun isTransientStatus(status: HttpStatusCode): Boolean {
        return status == HttpStatusCode.BadGateway ||
            status == HttpStatusCode.ServiceUnavailable ||
            status == HttpStatusCode.GatewayTimeout
    }

    private fun parseRetryAfter(headerValue: String?): Long {
        if (headerValue.isNullOrBlank()) return DEFAULT_RETRY_AFTER_SECONDS
        headerValue.toLongOrNull()?.let { if (it > 0) return it }

        return try {
            val targetInstant = ZonedDateTime.parse(headerValue, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            val diffSeconds = targetInstant.epochSecond - clock.instant().epochSecond
            diffSeconds.coerceAtLeast(1L)
        } catch (_: Exception) {
            DEFAULT_RETRY_AFTER_SECONDS
        }
    }
}
