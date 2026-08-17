package com.gametracker.backend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IgdbNamedItem(
    val id: Long? = null,
    val name: String
)

@Serializable
data class IgdbCover(
    val id: Long,
    val url: String? = null,
    @SerialName("image_id")
    val imageId: String? = null
)

@Serializable
data class IgdbGame(
    val id: Long,
    val name: String,
    val rating: Double? = null,
    val cover: IgdbCover? = null,
    val summary: String? = null,
    @SerialName("first_release_date")
    val firstReleaseDate: Long? = null,
    val genres: List<IgdbNamedItem>? = null,
    val platforms: List<IgdbNamedItem>? = null
)

@Serializable
data class GameDto(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val rating: Double?,
    val releaseDateEpochSeconds: Long?,
    val summary: String?,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList()
)

fun IgdbGame.toDto(): GameDto {
    val coverUrl = this.cover?.imageId?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg" }
        ?: this.cover?.url?.replace("t_thumb", "t_cover_big")?.let { if (it.startsWith("//")) "https:$it" else it }

    return GameDto(
        id = this.id,
        name = this.name,
        coverUrl = coverUrl,
        rating = this.rating,
        releaseDateEpochSeconds = this.firstReleaseDate,
        summary = this.summary,
        genres = this.genres?.map { it.name } ?: emptyList(),
        platforms = this.platforms?.map { it.name } ?: emptyList()
    )
}
