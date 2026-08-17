package com.gametracker.backend.auth

/**
 * Менеджер управления доступом к IGDB API через Twitch OAuth2.
 */
interface IgdbTokenManager {
    /**
     * Возвращает действующий OAuth Bearer токен (из кэша или запрашивая новый).
     */
    suspend fun getValidAccessToken(): String

    /**
     * Атомарно инвалидирует токен при ошибке 401 Unauthorized, если текущий токен совпадает с [badToken].
     */
    fun invalidateToken(badToken: String)
}
