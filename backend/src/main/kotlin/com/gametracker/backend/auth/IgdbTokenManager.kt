package com.gametracker.backend.auth

/**
 * Контракт менеджера OAuth2 токенов Twitch / IGDB API.
 * Отвечает за безопасное получение, ротацию и кэширование access token.
 */
interface IgdbTokenManager {
    /**
     * Возвращает валидный access_token (берет из кэша памяти или запрашивает новый у Twitch).
     */
    suspend fun getValidAccessToken(): String
    
    /**
     * Принудительно очищает кэш токена, чтобы следующий вызов [getValidAccessToken] запросил новый.
     * Используется, если IGDB вернул 401 Unauthorized до истечения срока действия токена.
     */
    fun forceRefresh()
}
