package io.github.typenil.gametracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Column value types for the JSON-encoded list columns of [GameDetailsEntity].
 * Deliberately NOT domain types from `core/model`: persisting domain classes here
 * would couple the domain layer to kotlinx.serialization (layer boundary, standard 4.3.5).
 * Translation between domain and these columns lives in EntityMappers.
 */
@Serializable
data class ReleaseDateColumn(
    val platform: String,
    val dateEpochSeconds: Long? = null,
    val year: Int? = null
)

@Serializable
data class CompanyColumn(
    val name: String,
    val isDeveloper: Boolean = false,
    val isPublisher: Boolean = false
)

@Serializable
data class VideoColumn(
    val videoId: String,
    val name: String? = null
)

@Serializable
data class SimilarGameColumn(
    val id: Long,
    val name: String? = null,
    val coverUrl: String? = null,
    val totalRating: Double? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
)

/**
 * Room entity caching the enriched details response (schema v5).
 *
 * Intentionally WITHOUT a foreign key to `games`: this row is a derived,
 * re-fetchable snapshot that is self-contained for rendering (similar games are
 * stored inline). An FK would either block catalog eviction (RESTRICT) or
 * cascade-delete the visible screen (CASCADE).
 */
@Entity(tableName = "game_details")
data class GameDetailsEntity(
    @PrimaryKey val gameId: Long,
    val name: String,
    val coverUrl: String?,
    val rating: Double?,
    val totalRating: Double?,
    val totalRatingCount: Long?,
    val releaseDateEpochSeconds: Long?,
    val summary: String?,
    val url: String?,
    val genres: List<String>,
    val themes: List<String>,
    val gameModes: List<String>,
    val platforms: List<String>,
    val releaseDates: List<ReleaseDateColumn>,
    val companies: List<CompanyColumn>,
    val screenshots: List<String>,
    val videos: List<VideoColumn>,
    val similarGames: List<SimilarGameColumn>,
    val cachedAtEpochSeconds: Long,
    val artworkUrl: String? = null,
    val timeToBeatMainSeconds: Long? = null,
    val timeToBeatCompleteSeconds: Long? = null
)
