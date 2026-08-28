package io.github.typenil.gametracker.core.designsystem.component

internal const val MAX_CARD_TAGS_PER_ROW = 2
private const val MAX_CARD_GENRES = 2
/** Up to two cleaned genre tags. */
fun selectGenreTags(genres: List<String>): List<String> =
    cleanTags(genres).take(MAX_CARD_GENRES)

private fun cleanTags(tags: List<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
