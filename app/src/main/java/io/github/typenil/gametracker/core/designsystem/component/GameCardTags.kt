package io.github.typenil.gametracker.core.designsystem.component

internal const val MAX_CARD_TAGS_PER_ROW = 2
private const val MAX_CARD_GENRES = 2
/** Up to two cleaned genre tags. */
fun selectGenreTags(genres: List<String>): List<String> =
    cleanTags(genres).take(MAX_CARD_GENRES)

/** Formats verbose genre names into compact chip labels. */
fun formatGenreTag(tag: String): String = when (tag.trim()) {
    "Role-playing (RPG)" -> "RPG"
    "Hack and slash/Beat 'em up" -> "Hack & Slash"
    "Turn-based strategy (TBS)" -> "TBS"
    "Real-time strategy (RTS)" -> "RTS"
    "Massively Multiplayer Online (MMO)" -> "MMO"
    "Card & Board Game" -> "Card Game"
    "Visual Novel" -> "Visual Novel"
    "Point-and-click" -> "Point & Click"
    "Quiz/Trivia" -> "Trivia"
    else -> tag.trim()
}

private fun cleanTags(tags: List<String>): List<String> =
    tags.map { formatGenreTag(it) }.filter { it.isNotEmpty() }.distinct()
