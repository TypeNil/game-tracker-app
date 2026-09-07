package com.gametracker.backend.auth

interface IgdbTokenManager {
    suspend fun getValidAccessToken(): String

    /**
     * Atomically invalidates the cached token on a 401 Unauthorized, but only if the current token equals [badToken].
     */
    fun invalidateToken(badToken: String)
}
