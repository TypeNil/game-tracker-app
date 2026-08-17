package com.gametracker.backend.auth

/**
 * Контракт менеджера OAuth2 токенов Twitch / IGDB API.
 * Отвечает за безопасное получение, ротацию и кэширование access token.
 */
interface IgdbTokenManager {
    suspend fun getValidAccessToken(): String
}
