package com.gametracker.backend.igdb

import com.gametracker.backend.application.IgdbConfig
import com.gametracker.backend.auth.IgdbTokenManager
import com.gametracker.backend.models.IgdbGame
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.slf4j.LoggerFactory

class IgdbService(
    private val httpClient: HttpClient,
    private val tokenManager: IgdbTokenManager,
    private val config: IgdbConfig
) {
    private val logger = LoggerFactory.getLogger("IgdbService")

    suspend fun queryGames(apicalypseQuery: String): List<IgdbGame> {
        logger.info("Executing IGDB query: {}", apicalypseQuery)
        val token = tokenManager.getValidAccessToken()
        
        return httpClient.post("https://api.igdb.com/v4/games") {
            header("Client-ID", config.clientId)
            header("Authorization", "Bearer $token")
            header("Accept", "application/json")
            setBody(apicalypseQuery)
        }.body()
    }
}
