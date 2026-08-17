package com.gametracker.backend.auth

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.error.UpstreamBadGatewayException
import com.gametracker.backend.error.UpstreamRateLimitException
import com.gametracker.backend.error.UpstreamServiceUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

data class TokenState(
    val token: String,
    val expiresAt: Instant
)

class IgdbTokenManagerImpl(
    private val config: IgdbConfig,
    private val httpClient: HttpClient,
    private val clock: Clock = Clock.systemUTC()
) : IgdbTokenManager {

    private val logger = LoggerFactory.getLogger("IgdbTokenManager")
    private val mutex = Mutex()
    private val state = AtomicReference<TokenState?>(null)

    override suspend fun getValidAccessToken(): String {
        // Быстрая проверка без блокировки мьютекса
        val currentState = state.get()
        if (currentState != null && isTokenValid(currentState)) {
            return currentState.token
        }

        return mutex.withLock {
            // Повторная проверка внутри критической секции
            val lockedState = state.get()
            if (lockedState != null && isTokenValid(lockedState)) {
                return@withLock lockedState.token
            }

            logger.info("Fetching new OAuth token from Twitch...")
            val response = fetchNewToken()

            val expiresAt = clock.instant().plusSeconds(response.expiresIn)
            val newState = TokenState(response.accessToken, expiresAt)
            state.set(newState)

            logger.info("New token obtained, expires at {}", expiresAt)
            newState.token
        }
    }

    override fun invalidateToken(badToken: String) {
        val updated = state.updateAndGet { current ->
            if (current?.token == badToken) null else current
        }
        if (updated == null) {
            logger.info("Token forcefully invalidated (CAS match). Next request will fetch a new token.")
        } else {
            logger.debug("Token invalidation skipped: bad token was already replaced by a newer token.")
        }
    }

    private fun isTokenValid(tokenState: TokenState): Boolean {
        // Оставляем запас времени (buffer) в 60 секунд ровно один раз
        return clock.instant().isBefore(tokenState.expiresAt.minusSeconds(60))
    }

    private suspend fun fetchNewToken(): TwitchTokenResponse {
        val response = httpClient.post("https://id.twitch.tv/oauth2/token") {
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

        if (!response.status.isSuccess()) {
            val status = response.status
            logger.error("Twitch OAuth token request failed with status {}", status)
            when (status) {
                HttpStatusCode.TooManyRequests -> throw UpstreamRateLimitException(retryAfterSeconds = 10)
                HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout ->
                    throw UpstreamServiceUnavailableException("Twitch OAuth service unavailable ($status)")
                else -> throw UpstreamBadGatewayException("Failed to acquire OAuth token from Twitch ($status)")
            }
        }

        return try {
            response.body<TwitchTokenResponse>()
        } catch (e: Exception) {
            throw UpstreamBadGatewayException("Failed to parse Twitch OAuth token response", e)
        }
    }
}
