package com.gametracker.backend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val firstReleaseDate: Long? = null
)

@Serializable
data class GameDto(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val rating: Double?,
    val releaseDate: Long?,
    val summary: String?
)

fun IgdbGame.toDto(): GameDto {
    // IGDB returns thumbnail URLs by default. We transform it to t_cover_big for high-res cover.
    val coverUrl = this.cover?.imageId?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg" } 
        ?: this.cover?.url?.replace("t_thumb", "t_cover_big")?.let { if (it.startsWith("//")) "https:$it" else it }
    
    return GameDto(
        id = this.id,
        name = this.name,
        coverUrl = coverUrl,
        rating = this.rating,
        releaseDate = this.firstReleaseDate,
        summary = this.summary
    )
}
