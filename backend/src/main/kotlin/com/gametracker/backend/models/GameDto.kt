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

/**
 * Nullable-расширение named-справочника (themes, game_modes, platform у release_dates).
 * Не переиспользует [IgdbNamedItem]: обязательное `name` означало бы, что один
 * вложенный объект без name валит декод всего List<IgdbGame> (маскируясь под 502).
 */
@Serializable
data class IgdbNamedExpansion(
    val id: Long? = null,
    val name: String? = null,
    val abbreviation: String? = null
)

@Serializable
data class IgdbScreenshot(
    val id: Long? = null,
    @SerialName("image_id")
    val imageId: String? = null
)

@Serializable
data class IgdbVideo(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("video_id")
    val videoId: String? = null
)

@Serializable
data class IgdbReleaseDate(
    val id: Long? = null,
    val date: Long? = null,
    val y: Int? = null,
    val platform: IgdbNamedExpansion? = null
)

@Serializable
data class IgdbInvolvedCompany(
    val id: Long? = null,
    val company: IgdbNamedExpansion? = null,
    val developer: Boolean? = null,
    val publisher: Boolean? = null
)

/** Обложка похожей игры — выделенный тип с полностью optional-полями. */
@Serializable
data class IgdbSimilarCover(
    val id: Long? = null,
    @SerialName("image_id")
    val imageId: String? = null
)

@Serializable
data class IgdbSimilarGame(
    val id: Long,
    val name: String? = null,
    val cover: IgdbSimilarCover? = null,
    @SerialName("total_rating")
    val totalRating: Double? = null
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
    val platforms: List<IgdbNamedItem>? = null,
    // Details-only expansions: списковые запросы эти поля не запрашивают,
    // поэтому там они декодируются в null. IGDB опускает отсутствующие
    // sub-объекты целиком (не пишет null-литералы), что совместимо с дефолтами.
    val url: String? = null,
    @SerialName("total_rating")
    val totalRating: Double? = null,
    @SerialName("total_rating_count")
    val totalRatingCount: Long? = null,
    val themes: List<IgdbNamedExpansion>? = null,
    @SerialName("game_modes")
    val gameModes: List<IgdbNamedExpansion>? = null,
    @SerialName("release_dates")
    val releaseDates: List<IgdbReleaseDate>? = null,
    @SerialName("involved_companies")
    val involvedCompanies: List<IgdbInvolvedCompany>? = null,
    val screenshots: List<IgdbScreenshot>? = null,
    val videos: List<IgdbVideo>? = null,
    @SerialName("similar_games")
    val similarGames: List<IgdbSimilarGame>? = null
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

@Serializable
data class GameReleaseDateDto(
    val platform: String,
    val dateEpochSeconds: Long? = null,
    val year: Int? = null
)

@Serializable
data class GameCompanyDto(
    val name: String,
    val isDeveloper: Boolean = false,
    val isPublisher: Boolean = false
)

@Serializable
data class GameVideoDto(
    val videoId: String,
    val name: String? = null
)

@Serializable
data class SimilarGameDto(
    val id: Long,
    val name: String? = null,
    val coverUrl: String? = null,
    val totalRating: Double? = null
)

/**
 * Ответ GET /v1/games/{id}. Первые восемь полей повторяют контракт спискового
 * [GameDto] без переименований; остальные — details-only. rating — критический
 * рейтинг IGDB (как в списках), totalRating — агрегированный (критики+игроки).
 */
@Serializable
data class GameDetailsDto(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val rating: Double?,
    val releaseDateEpochSeconds: Long?,
    val summary: String?,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val url: String? = null,
    val totalRating: Double? = null,
    val totalRatingCount: Long? = null,
    val themes: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val releaseDates: List<GameReleaseDateDto> = emptyList(),
    val companies: List<GameCompanyDto> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val videos: List<GameVideoDto> = emptyList(),
    val similarGames: List<SimilarGameDto> = emptyList()
)

private const val IMAGE_SIZE_COVER_BIG = "t_cover_big"
private const val IMAGE_SIZE_720P = "t_720p"

// Apicalypse не умеет лимитить sub-запросы: IGDB отдаёт все связанные сущности,
// поэтому трим делается на стороне BFF до отдачи клиенту.
private const val MAX_SCREENSHOTS = 8
private const val MAX_VIDEOS = 5
private const val MAX_SIMILAR_GAMES = 10
private const val MAX_RELEASE_DATES = 12

private fun igdbImageUrl(imageId: String?, rawUrl: String?, size: String): String? =
    imageId?.takeIf { it.isNotBlank() }
        ?.let { "https://images.igdb.com/igdb/image/upload/$size/$it.jpg" }
        ?: rawUrl?.replace("t_thumb", size)?.let { if (it.startsWith("//")) "https:$it" else it }

private fun List<IgdbNamedExpansion>?.toNameList(): List<String> =
    orEmpty().mapNotNull { item -> item.name?.trim()?.takeIf(String::isNotEmpty) }

private fun List<IgdbReleaseDate>?.toReleaseDateList(): List<GameReleaseDateDto> =
    orEmpty()
        .mapNotNull { releaseDate ->
            val platform = releaseDate.platform?.abbreviation?.takeIf { it.isNotBlank() }
                ?: releaseDate.platform?.name?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            GameReleaseDateDto(
                platform = platform,
                dateEpochSeconds = releaseDate.date,
                year = releaseDate.y
            )
        }
        // Записи с точной датой выигрывают дедупликацию по (platform, year)
        .sortedByDescending { it.dateEpochSeconds != null }
        .distinctBy { it.platform to it.year }
        .take(MAX_RELEASE_DATES)
        .sortedBy { it.dateEpochSeconds ?: it.year?.toLong() ?: Long.MAX_VALUE }

private fun List<IgdbInvolvedCompany>?.toCompanyList(): List<GameCompanyDto> =
    orEmpty().mapNotNull { involved ->
        val name = involved.company?.name?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        GameCompanyDto(
            name = name,
            isDeveloper = involved.developer == true,
            isPublisher = involved.publisher == true
        )
    }

private fun List<IgdbScreenshot>?.toScreenshotUrlList(): List<String> =
    orEmpty()
        .mapNotNull { it.imageId?.takeIf(String::isNotBlank) }
        .map { imageId -> igdbImageUrl(imageId, rawUrl = null, IMAGE_SIZE_720P) }
        .filterNotNull()
        .take(MAX_SCREENSHOTS)

private fun List<IgdbVideo>?.toVideoList(): List<GameVideoDto> =
    orEmpty()
        .mapNotNull { video ->
            val videoId = video.videoId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            GameVideoDto(videoId = videoId, name = video.name?.trim()?.takeIf(String::isNotEmpty))
        }
        .take(MAX_VIDEOS)

private fun List<IgdbSimilarGame>?.toSimilarGameList(): List<SimilarGameDto> =
    orEmpty()
        .map { similar ->
            SimilarGameDto(
                id = similar.id,
                name = similar.name?.trim()?.takeIf(String::isNotEmpty),
                coverUrl = similar.cover?.let { igdbImageUrl(it.imageId, rawUrl = null, IMAGE_SIZE_COVER_BIG) },
                totalRating = similar.totalRating
            )
        }
        .take(MAX_SIMILAR_GAMES)

fun IgdbGame.toDto(): GameDto = GameDto(
    id = this.id,
    name = this.name,
    coverUrl = igdbImageUrl(this.cover?.imageId, this.cover?.url, IMAGE_SIZE_COVER_BIG),
    rating = this.rating,
    releaseDateEpochSeconds = this.firstReleaseDate,
    summary = this.summary,
    genres = this.genres?.map { it.name } ?: emptyList(),
    platforms = this.platforms?.map { it.name } ?: emptyList()
)

fun IgdbGame.toGameDto(): GameDto = toDto()

fun IgdbGame.toDetailsDto(): GameDetailsDto = GameDetailsDto(
    id = this.id,
    name = this.name,
    coverUrl = igdbImageUrl(this.cover?.imageId, this.cover?.url, IMAGE_SIZE_COVER_BIG),
    rating = this.rating,
    releaseDateEpochSeconds = this.firstReleaseDate,
    summary = this.summary,
    genres = this.genres?.map { it.name } ?: emptyList(),
    platforms = this.platforms?.map { it.name } ?: emptyList(),
    url = this.url,
    totalRating = this.totalRating,
    totalRatingCount = this.totalRatingCount,
    themes = this.themes.toNameList(),
    gameModes = this.gameModes.toNameList(),
    releaseDates = this.releaseDates.toReleaseDateList(),
    companies = this.involvedCompanies.toCompanyList(),
    screenshots = this.screenshots.toScreenshotUrlList(),
    videos = this.videos.toVideoList(),
    similarGames = this.similarGames.toSimilarGameList()
)
