package com.gametracker.backend.auth

import com.gametracker.backend.application.IgdbConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant

class IgdbTokenManagerImpl(
    private val config: IgdbConfig,
    private val httpClient: HttpClient
) : IgdbTokenManager {

    private val logger = LoggerFactory.getLogger("IgdbTokenManager")
    private val mutex = Mutex()

    @Volatile
    private var cachedToken: String? = null
    
    @Volatile
    private var expiresAt: Instant = Instant.MIN

    override suspend fun getValidAccessToken(): String {
        // Double-checked locking для оптимизации: если токен валиден, возвращаем без лока
        if (isTokenValid()) {
            return cachedToken!!
        }

        return mutex.withLock {
            // Повторная проверка внутри лока
            if (isTokenValid()) {
                return@withLock cachedToken!!
            }

            logger.info("Fetching new OAuth token from Twitch...")
            val response = fetchNewToken()
            
            cachedToken = response.accessToken
            // Оставляем запас времени (buffer) в 60 секунд, чтобы токен не протух в момент пересылки
            expiresAt = Instant.now().plusSeconds(response.expiresIn - 60)

            logger.info("New token obtained, expires at {}", expiresAt)
            cachedToken!!
        }
    }

    private fun isTokenValid(): Boolean {
        // Оставляем запас (buffer) в 60 секунд, чтобы токен не протух во время самого запроса
        return cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))
    }

    override fun forceRefresh() {
        cachedToken = null
        expiresAt = Instant.MIN
        logger.info("Token forcefully invalidated. Next request will fetch a new token.")
    }

    private suspend fun fetchNewToken(): TwitchTokenResponse {
        val url = "https://id.twitch.tv/oauth2/token?client_id=${config.clientId}&client_secret=${config.clientSecret}&grant_type=client_credentials"
        return httpClient.post(url) {
            contentType(ContentType.Application.FormUrlEncoded)
        }.body()
    }
}
