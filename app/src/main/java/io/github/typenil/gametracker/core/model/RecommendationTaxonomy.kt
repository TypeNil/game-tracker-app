package io.github.typenil.gametracker.core.model

/**
 * Canonical IGDB genre names used by Search filters and For You cold-start prefs.
 */
object RecommendationGenreCatalog {
    val wireNames: List<String> = listOf(
        "Role-playing (RPG)",
        "Action",
        "Adventure",
        "Shooter",
        "Strategy",
        "Turn-based strategy (TBS)",
        "Real-time strategy (RTS)",
        "Platform",
        "Puzzle",
        "Indie",
        "Simulator",
        "Sport",
        "Racing",
        "Fighting",
        "Hack and slash/Beat 'em up",
        "Music",
        "Arcade",
        "Visual Novel",
        "Point-and-click",
        "Tactical",
        "MOBA",
        "Card & Board Game",
    )
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
