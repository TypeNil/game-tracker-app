package com.gametracker.backend.error

import kotlinx.serialization.Serializable

/**
 * Единый формат ответа об ошибке для всех эндпоинтов BFF.
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
