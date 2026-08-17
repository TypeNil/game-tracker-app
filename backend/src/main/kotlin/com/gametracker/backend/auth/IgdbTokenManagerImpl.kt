package com.gametracker.backend.auth

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.error.UpstreamBadGatewayException
import com.gametracker.backend.error.UpstreamRateLimitException
import com.gametracker.backend.error.UpstreamServiceUnavailableException
import com.gametracker.backend.error.UpstreamTimeoutException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.discardRemaining
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

private const val EXPIRATION_BUFFER_SECONDS = 60L
private const val DEFAULT_RETRY_AFTER_SECONDS = 5L
private const val MILLIS_PER_SECOND = 1000L

class IgdbTokenManagerImpl(
    private val config: IgdbConfig,
    private val httpClient: HttpClient,
    private val clock: Clock = Clock.systemUTC()
) : IgdbTokenManager {

    private val logger = LoggerFactory.getLogger("IgdbTokenManager")
    private val tokenState = AtomicReference<TokenState?>(null)
    private val mutex = Mutex()

    override suspend fun getValidAccessToken(): String {
        val now = clock.instant().epochSecond
        val current = tokenState.get()

        if (current != null && current.expiresAtEpochSeconds - now > EXPIRATION_BUFFER_SECONDS) {
            return current.token
        }

        return mutex.withLock {
            val lockedNow = clock.instant().epochSecond
            val lockedCurrent = tokenState.get()

            if (lockedCurrent != null && lockedCurrent.expiresAtEpochSeconds - lockedNow > EXPIRATION_BUFFER_SECONDS) {
                return@withLock lockedCurrent.token
            }

            val newToken = fetchNewToken()
            val newState = TokenState(
                token = newToken.accessToken,
                expiresAtEpochSeconds = lockedNow + newToken.expiresIn
            )
            tokenState.set(newState)
            newState.token
        }
    }

    override fun invalidateToken(badToken: String) {
        val updated = tokenState.updateAndGet { current ->
            if (current != null && current.token == badToken) null else current
        }
        if (updated == null) {
            logger.info("Invalidated expired/rejected access token from memory cache.")
        }
    }

    private suspend fun fetchNewToken(): TwitchTokenResponse {
        logger.info("Fetching new OAuth token from Twitch...")

        val response: HttpResponse = try {
            httpClient.post("https://id.twitch.tv/oauth2/token") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", config.clientId)
                            append("client_secret", config.clientSecret)
                            append("grant_type", "client_credentials")
                        }
                    )
                )
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            when (e) {
                is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException ->
                    throw UpstreamTimeoutException("Twitch OAuth token request timed out", e)
                is IOException ->
                    throw UpstreamServiceUnavailableException("Failed to connect to Twitch OAuth service", e)
                else ->
                    throw UpstreamBadGatewayException("Unexpected error during Twitch OAuth request", e)
            }
        }

        return try {
            if (!response.status.isSuccess()) {
                handleTwitchErrorStatus(response)
            }

            val tokenResponse = response.body<TwitchTokenResponse>()
            logger.info(
                "New token obtained, expires at {}",
                Instant.ofEpochMilli(clock.millis() + tokenResponse.expiresIn * MILLIS_PER_SECOND)
            )
            tokenResponse
        } catch (e: Throwable) {
            when (e) {
                is CancellationException,
                is UpstreamRateLimitException,
                is UpstreamBadGatewayException,
                is UpstreamServiceUnavailableException,
                is UpstreamTimeoutException -> throw e
                is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException ->
                    throw UpstreamTimeoutException("Twitch OAuth response reading timed out", e)
                is IOException ->
                    throw UpstreamServiceUnavailableException("I/O error reading Twitch OAuth response", e)
                else -> {
                    logger.error("Failed to decode Twitch OAuth token response", e)
                    throw UpstreamBadGatewayException("Failed to decode Twitch OAuth token response", e)
                }
            }
        } finally {
            response.discardRemaining()
        }
    }

    private fun handleTwitchErrorStatus(response: HttpResponse): Nothing {
        val status = response.status
        logger.error("Twitch OAuth failed with status {}", status)

        when (status) {
            HttpStatusCode.TooManyRequests -> {
                val retryAfterHeader = response.headers[HttpHeaders.RetryAfter]
                val retryAfterSeconds = parseRetryAfter(retryAfterHeader)
                throw UpstreamRateLimitException(retryAfterSeconds)
            }
            HttpStatusCode.ServiceUnavailable -> {
                throw UpstreamServiceUnavailableException("Twitch OAuth service unavailable")
            }
            HttpStatusCode.GatewayTimeout, HttpStatusCode.RequestTimeout -> {
                throw UpstreamTimeoutException("Twitch OAuth gateway timeout")
            }
            else -> {
                throw UpstreamBadGatewayException("Twitch OAuth request returned status ${status.value}")
            }
        }
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

    private data class TokenState(
        val token: String,
        val expiresAtEpochSeconds: Long
    )
}
