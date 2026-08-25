package io.github.typenil.gametracker.core.model

data class GamePage(
    val items: List<Game>,
    val nextOffset: Int?,
    val endReached: Boolean,
)

data class RecommendationCandidatePage(
    val items: List<RecommendationCandidate>,
    val nextOffset: Int?,
    val endReached: Boolean,
)
