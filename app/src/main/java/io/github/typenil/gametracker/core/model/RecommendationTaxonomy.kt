package io.github.typenil.gametracker.core.model

enum class RecommendationTagAxis {
    GENRE,
    THEME,
}

data class RecommendationTag(
    val wireName: String,
    val axis: RecommendationTagAxis,
)

/**
 * Ordered onboarding/search chips. Action is an IGDB theme, not a genre.
 */
object RecommendationTagCatalog {
    val tags: List<RecommendationTag> = listOf(
        RecommendationTag("Role-playing (RPG)", RecommendationTagAxis.GENRE),
        RecommendationTag("Action", RecommendationTagAxis.THEME),
        RecommendationTag("Adventure", RecommendationTagAxis.GENRE),
        RecommendationTag("Shooter", RecommendationTagAxis.GENRE),
        RecommendationTag("Strategy", RecommendationTagAxis.GENRE),
        RecommendationTag("Turn-based strategy (TBS)", RecommendationTagAxis.GENRE),
        RecommendationTag("Real-time strategy (RTS)", RecommendationTagAxis.GENRE),
        RecommendationTag("Platform", RecommendationTagAxis.GENRE),
        RecommendationTag("Puzzle", RecommendationTagAxis.GENRE),
        RecommendationTag("Indie", RecommendationTagAxis.GENRE),
        RecommendationTag("Simulator", RecommendationTagAxis.GENRE),
        RecommendationTag("Sport", RecommendationTagAxis.GENRE),
        RecommendationTag("Racing", RecommendationTagAxis.GENRE),
        RecommendationTag("Fighting", RecommendationTagAxis.GENRE),
        RecommendationTag("Hack and slash/Beat 'em up", RecommendationTagAxis.GENRE),
        RecommendationTag("Music", RecommendationTagAxis.GENRE),
        RecommendationTag("Arcade", RecommendationTagAxis.GENRE),
        RecommendationTag("Visual Novel", RecommendationTagAxis.GENRE),
        RecommendationTag("Point-and-click", RecommendationTagAxis.GENRE),
        RecommendationTag("Tactical", RecommendationTagAxis.GENRE),
        RecommendationTag("MOBA", RecommendationTagAxis.GENRE),
        RecommendationTag("Card & Board Game", RecommendationTagAxis.GENRE),
    )

    val wireNames: List<String> = tags.map { it.wireName }

    val genreNames: Set<String> = tags
        .filter { it.axis == RecommendationTagAxis.GENRE }
        .mapTo(mutableSetOf()) { it.wireName }

    val themeNames: Set<String> = tags
        .filter { it.axis == RecommendationTagAxis.THEME }
        .mapTo(mutableSetOf()) { it.wireName }

    fun split(selected: Set<String>): Pair<Set<String>, Set<String>> {
        val genres = selected.filterTo(mutableSetOf()) { it in genreNames }
        val themes = selected.filterTo(mutableSetOf()) { it in themeNames }
        return genres to themes
    }
}

object RecommendationGenreCatalog {
    val wireNames: List<String> = RecommendationTagCatalog.tags
        .filter { it.axis == RecommendationTagAxis.GENRE }
        .map { it.wireName }
}

enum class RecommendationPlatformFamily(val storageId: String) {
    PLAYSTATION("PLAYSTATION"),
    XBOX("XBOX"),
    NINTENDO("NINTENDO"),
    PC("PC"),
    ;

    fun toIgdbNames(): List<String> = when (this) {
        PLAYSTATION -> listOf(
            "PlayStation 5", "PlayStation 4", "PlayStation 3",
            "PlayStation 2", "PlayStation", "PlayStation Vita", "PSP",
        )
        XBOX -> listOf(
            "Xbox Series X|S", "Xbox One", "Xbox 360", "Xbox",
        )
        NINTENDO -> listOf(
            "Nintendo Switch", "Nintendo Switch 2", "Wii U", "Wii",
            "Nintendo 3DS", "Nintendo DS", "Nintendo 64", "SNES", "NES",
        )
        PC -> listOf(
            "PC (Microsoft Windows)", "Mac", "Linux", "SteamOS",
        )
    }

    companion object {
        fun fromStorageId(id: String): RecommendationPlatformFamily? =
            entries.find { it.storageId == id }
    }
}

fun expandRecommendationPlatforms(storageIds: Set<String>): Set<String> =
    storageIds.mapNotNull(RecommendationPlatformFamily::fromStorageId)
        .flatMap { it.toIgdbNames() }
        .toSet()
