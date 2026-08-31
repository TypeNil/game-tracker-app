package io.github.typenil.gametracker.feature.details

import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.model.GameReleaseDate
const val HEADER_TAGS_PREVIEW_LIMIT = 3
const val PLATFORMS_PREVIEW_LIMIT = 2

data class HeaderTagsPreview(
    val previewTags: List<String>,
    val overflowCount: Int,
)

data class PlatformsPreview(
    val previewText: String,
    val overflowCount: Int,
)

data class PlatformReleaseItem(
    val platform: String,
    val displayDate: String?,
)

/** Formats genres and themes for the compact header preview. */
fun formatHeaderTagPreview(
    genres: List<String>,
    themes: List<String>,
    limit: Int = HEADER_TAGS_PREVIEW_LIMIT,
): HeaderTagsPreview {
    val allTags = (genres + themes).map { formatGenreTag(it) }.distinct()
    if (allTags.size <= limit) {
        return HeaderTagsPreview(previewTags = allTags, overflowCount = 0)
    }
    val preview = allTags.take(limit)
    return HeaderTagsPreview(previewTags = preview, overflowCount = allTags.size - limit)
}

/** Formats platform names for the 2-column fact card preview. */
fun formatPlatformsPreview(
    platforms: List<String>,
    limit: Int = PLATFORMS_PREVIEW_LIMIT,
): PlatformsPreview {
    if (platforms.isEmpty()) {
        return PlatformsPreview(previewText = "", overflowCount = 0)
    }
    if (platforms.size <= limit) {
        return PlatformsPreview(
            previewText = platforms.joinToString(", "),
            overflowCount = 0,
        )
    }
    val preview = platforms.take(limit).joinToString(", ")
    return PlatformsPreview(
        previewText = preview,
        overflowCount = platforms.size - limit,
    )
}

/**
 * Merges [platforms] and [releaseDates] into an exhaustive list of platform release items.
 * Dated platforms appear first (sorted by date), followed by undated platforms.
 */
fun mergePlatformsAndReleases(
    platforms: List<String>,
    releaseDates: List<GameReleaseDate>,
    unknownDateLabel: String,
): List<PlatformReleaseItem> {
    val datesByPlatform = releaseDates.associateBy { it.platform }
    val allPlatformNames = (platforms + releaseDates.map { it.platform }).distinct()

    return allPlatformNames.map { platformName ->
        val releaseDate = datesByPlatform[platformName]
        PlatformReleaseItem(
            platform = platformName,
            displayDate = releaseDate?.displayDate(unknownDateLabel),
        )
    }.sortedWith(
        compareBy<PlatformReleaseItem> { item ->
            val date = datesByPlatform[item.platform]
            when {
                date?.dateEpochSeconds != null -> 0
                date?.year != null -> 1
                else -> 2
            }
        }.thenBy { item ->
            val date = datesByPlatform[item.platform]
            date?.dateEpochSeconds ?: -(date?.year?.toLong() ?: 0L)
        }.thenBy { it.platform.lowercase() }
    )
}
