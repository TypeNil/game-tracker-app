package com.gametracker.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class GamePageDto(
    val items: List<GameDto> = emptyList(),
    val nextOffset: Int? = null,
    val endReached: Boolean = true,
)

@Serializable
data class RecommendationCandidatePageDto(
    val items: List<RecommendationCandidateDto> = emptyList(),
    val nextOffset: Int? = null,
    val endReached: Boolean = true,
)
