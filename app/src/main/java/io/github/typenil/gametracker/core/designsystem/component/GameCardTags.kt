package io.github.typenil.gametracker.core.designsystem.component

internal const val MAX_CARD_TAGS_PER_ROW = 3
private const val MAX_CARD_GENRES = 2

/** Up to two genres, then one platform that is not already a genre. */
fun selectCardTags(genres: List<String>, platforms: List<String>): List<String> {
    val selectedGenres = cleanTags(genres).take(MAX_CARD_GENRES)
    val platform = cleanTags(platforms).firstOrNull { it !in selectedGenres }
    return if (platform == null) selectedGenres else selectedGenres + platform
}

private fun cleanTags(tags: List<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
