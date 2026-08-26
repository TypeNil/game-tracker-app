package io.github.typenil.gametracker.core.model

/**
 * Pure domain model representing a video game in the catalog.
 * Free of Android/Compose UI framework dependencies.
 */
data class Game(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val rating: Double? = null,
    val releaseDateEpochSeconds: Long? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
)

fun Game.cardTags(max: Int = DEFAULT_CARD_TAG_MAX): List<String> =
    selectCardTags(genres, themes, platforms, max)

fun selectCardTags(
    genres: List<String>,
    themes: List<String>,
    platforms: List<String>,
    max: Int = DEFAULT_CARD_TAG_MAX,
): List<String> {
    if (max <= 0) return emptyList()
    val queues = listOf(genres, themes, platforms).map { tags ->
        ArrayDeque(tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
    }
    val result = ArrayList<String>(max)
    val seen = HashSet<String>()
    while (result.size < max) {
        var progressed = false
        for (queue in queues) {
            while (queue.isNotEmpty()) {
                val tag = queue.removeFirst()
                if (seen.add(tag)) {
                    result += tag
                    progressed = true
                    break
                }
            }
            if (result.size >= max) break
        }
        if (!progressed) break
    }
    return result
}

private const val DEFAULT_CARD_TAG_MAX = 4
