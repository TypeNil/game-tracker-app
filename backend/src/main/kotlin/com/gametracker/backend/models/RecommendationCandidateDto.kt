package com.gametracker.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class RecommendationCandidateDto(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val ratingCount: Long? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val similarToGameIds: List<Long> = emptyList(),
)

fun IgdbGame.toCandidateDto(similarToGameIds: List<Long> = emptyList()) = RecommendationCandidateDto(
    id = id,
    name = name,
    coverUrl = igdbImageUrl(cover?.imageId, cover?.url, IMAGE_SIZE_COVER_BIG),
    rating = rating,
    ratingCount = ratingCount,
    releaseDateEpochSeconds = firstReleaseDate,
    summary = summary,
    genres = genres?.map { it.name } ?: emptyList(),
    themes = themes.toNameList(),
    platforms = platforms?.map { it.name } ?: emptyList(),
    similarToGameIds = similarToGameIds,
)
