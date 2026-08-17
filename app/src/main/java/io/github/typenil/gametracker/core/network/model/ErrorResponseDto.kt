package io.github.typenil.gametracker.core.network.model

import kotlinx.serialization.Serializable

/**
 * Structured error response DTO returned by the Ktor BFF service.
 */
@Serializable
data class ErrorResponseDto(
    val code: String,
    val message: String,
    val timestamp: Long? = null
)
