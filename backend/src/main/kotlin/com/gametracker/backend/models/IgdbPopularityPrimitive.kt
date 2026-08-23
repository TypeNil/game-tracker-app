package com.gametracker.backend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IgdbPopularityPrimitive(
    @SerialName("game_id")
    val gameId: Long? = null,
    val value: Double? = null,
    @SerialName("popularity_type")
    val popularityType: Int? = null,
)
