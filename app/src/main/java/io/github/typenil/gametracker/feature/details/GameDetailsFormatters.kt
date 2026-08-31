package io.github.typenil.gametracker.feature.details

import io.github.typenil.gametracker.core.designsystem.component.formatGenreTag
import io.github.typenil.gametracker.core.designsystem.component.formatPlatformDisplayName
import io.github.typenil.gametracker.core.model.GameReleaseDate

const val HEADER_TAGS_PREVIEW_LIMIT = 3
const val PLATFORMS_PREVIEW_LIMIT = 2
const val PLATFORMS_PREVIEW_LIMIT_HALF = 2
const val PLATFORMS_PREVIEW_LIMIT_FULL = 4
const val GAME_MODES_PREVIEW_LIMIT = 2
data class HeaderTagsPreview(
    val previewTags: List<String>,
    val overflowCount: Int,
)

data class PlatformsPreview(
    val previewText: String,
    val overflowCount: Int,
)

data class GameModesPreview(
    val previewText: String,
    val overflowCount: Int,
)

data class PlatformReleaseItem(
    val platform: String,
    val displayDate: String?,
)

data class FactsTopology(
    val platformsFullWidth: Boolean,
)

fun resolveFactsTopology(
    hasRelease: Boolean,
    hasModes: Boolean,
    hasPlatforms: Boolean,
    hasTime: Boolean,
): FactsTopology {
    val visibleCount = listOf(hasRelease, hasModes, hasPlatforms, hasTime).count { it }
    return FactsTopology(
        platformsFullWidth = hasPlatforms && !hasTime && visibleCount % 2 == 1,
    )
}

/**
 * Formats genres and themes for the compact details header preview:
 * shows up to [HEADER_TAGS_PREVIEW_LIMIT] tags across two rows with overflow counter for the rest.
 */
fun formatHeaderTagPreview(
    genres: List<String>,
    themes: List<String>,
): HeaderTagsPreview {
    val allTags = (genres + themes)
        .map { formatGenreTag(it) }
        .filter { it.isNotBlank() }
        .distinct()
    val previewTags = allTags.take(HEADER_TAGS_PREVIEW_LIMIT)
    return HeaderTagsPreview(
        previewTags = previewTags,
        overflowCount = allTags.size - previewTags.size,
    )
}

/** Formats a game mode name for concise display. */
fun formatGameModeName(rawMode: String): String = when (rawMode.trim()) {
    "Co-operative" -> "Co-op"
    "Massively Multiplayer Online (MMO)" -> "MMO"
    "Single player" -> "Single-player"
    else -> rawMode.trim()
}

/** Formats platform names for the 2-column fact card preview. */
fun formatPlatformsPreview(
    platforms: List<String>,
    limit: Int = PLATFORMS_PREVIEW_LIMIT,
): PlatformsPreview {
    if (platforms.isEmpty()) {
        return PlatformsPreview(previewText = "", overflowCount = 0)
    }
    val formatted = platforms.map { formatPlatformDisplayName(it) }.distinct()
    if (formatted.size <= limit) {
        return PlatformsPreview(
            previewText = formatted.joinToString(", "),
            overflowCount = 0,
        )
    }
    val preview = formatted.take(limit).joinToString(", ")
    return PlatformsPreview(
        previewText = preview,
        overflowCount = formatted.size - limit,
    )
}

/** Formats game modes for the 2-column fact card preview. */
fun formatGameModesPreview(
    gameModes: List<String>,
    limit: Int = GAME_MODES_PREVIEW_LIMIT,
): GameModesPreview {
    if (gameModes.isEmpty()) {
        return GameModesPreview(previewText = "", overflowCount = 0)
    }
    val formatted = gameModes.map { formatGameModeName(it) }.distinct()
    if (formatted.size <= limit) {
        return GameModesPreview(
            previewText = formatted.joinToString(", "),
            overflowCount = 0,
        )
    }
    val preview = formatted.take(limit).joinToString(", ")
    return GameModesPreview(
        previewText = preview,
        overflowCount = formatted.size - limit,
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
    val datesByPlatform = mutableMapOf<String, GameReleaseDate>()
    for (releaseDate in releaseDates) {
        val normalized = formatPlatformDisplayName(releaseDate.platform)
        val existing = datesByPlatform[normalized]
        if (existing == null) {
            datesByPlatform[normalized] = releaseDate
        } else {
            val existingEpoch = existing.dateEpochSeconds
            val newEpoch = releaseDate.dateEpochSeconds
            when {
                existingEpoch == null && newEpoch != null -> datesByPlatform[normalized] = releaseDate
                existingEpoch != null && newEpoch != null && newEpoch < existingEpoch -> datesByPlatform[normalized] = releaseDate
                existingEpoch == null && newEpoch == null -> {
                    val existingYear = existing.year
                    val newYear = releaseDate.year
                    if (existingYear == null || (newYear != null && newYear < existingYear)) {
                        datesByPlatform[normalized] = releaseDate
                    }
                }
            }
        }
    }

    val allPlatformNames = (
        platforms.map { formatPlatformDisplayName(it) } +
            releaseDates.map { formatPlatformDisplayName(it.platform) }
    ).filter(String::isNotBlank).distinct()

    return allPlatformNames.map { platformName ->
        val releaseDate = datesByPlatform[platformName]
        PlatformReleaseItem(
            platform = platformName,
            displayDate = releaseDate?.displayDate(unknownDateLabel) ?: unknownDateLabel,
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
            date?.dateEpochSeconds
                ?: date?.year?.let { year ->
                    java.time.Year.of(year).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
                }
                ?: Long.MAX_VALUE
        }.thenBy { it.platform.lowercase() }
    )
}
