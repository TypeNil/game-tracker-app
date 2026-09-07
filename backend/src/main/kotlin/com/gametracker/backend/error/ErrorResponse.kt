package com.gametracker.backend.error

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
