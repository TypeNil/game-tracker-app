package com.gametracker.backend.igdb

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.models.IgdbGame
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory

class IgdbService(
    private val httpClient: HttpClient,
    private val tokenManager: IgdbTokenManager,
    private val config: IgdbConfig
) {
    private val logger = LoggerFactory.getLogger("IgdbService")
    // Максимум 4 одновременных запроса к IGDB (upstream concurrency limit)
    private val concurrencySemaphore = Semaphore(4)

    suspend fun queryGames(apicalypseQuery: String): List<IgdbGame> {
        logger.info("Executing IGDB query: {}", apicalypseQuery)
        
        val response = concurrencySemaphore.withPermit {
            executeWithRetry { token ->
                httpClient.post("https://api.igdb.com/v4/games") {
                    header("Client-ID", config.clientId)
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                    setBody(apicalypseQuery)
                }
            }
        }
        return response.body()
    }

    private suspend fun executeWithRetry(
        block: suspend (String) -> HttpResponse
    ): HttpResponse {
        var token = tokenManager.getValidAccessToken()
        var response = block(token)

        // Один refresh/retry после upstream 401
        if (response.status == HttpStatusCode.Unauthorized) {
            logger.warn("Received 401 Unauthorized from IGDB. Forcing token refresh and retrying once.")
            tokenManager.forceRefresh()
            token = tokenManager.getValidAccessToken()
            response = block(token)
            
            if (response.status == HttpStatusCode.Unauthorized) {
                // Если даже после обновления токена мы получили 401, кидаем ошибку
                throw IllegalStateException("IGDB returned 401 Unauthorized even after token refresh.")
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            logger.error("IGDB request failed with status {}: {}", response.status, errorBody)
            throw RuntimeException("Upstream API error: ${response.status.value}")
        }

        return response
    }
}
