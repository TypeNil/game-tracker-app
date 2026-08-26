package io.github.typenil.gametracker.core.designsystem.component

const val MAX_CARD_TAGS = 3
const val MAX_CARD_TAGS_PER_ROW = 3
private const val MAX_CARD_GENRES = 2
private const val MAX_CARD_PLATFORMS = 1

fun selectCardTags(
    genres: List<String>,
    platforms: List<String>,
    max: Int = MAX_CARD_TAGS,
): List<String> {
    if (max <= 0) return emptyList()
    val seen = LinkedHashSet<String>()
    for (genre in cleanTags(genres)) {
        if (seen.size >= max) break
        if (seen.add(genre) && seen.size >= MAX_CARD_GENRES) break
    }
    var platformsAdded = 0
    for (platform in cleanTags(platforms)) {
        if (seen.size >= max || platformsAdded >= MAX_CARD_PLATFORMS) break
        if (seen.add(platform)) platformsAdded++
    }
    return seen.toList()
}

private fun cleanTags(tags: List<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
