package io.github.typenil.gametracker.core.network.datasource

import io.github.typenil.gametracker.core.network.model.GameDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock implementation of [BffRemoteDataSource] used in the `demo` flavor.
 * Allows running the entire application offline without a local backend instance.
 */
@Singleton
class FakeBffDataSource @Inject constructor() : BffRemoteDataSource {

    private val mockGames = listOf(
        GameDto(
            id = 1942L,
            name = "The Witcher 3: Wild Hunt",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg",
            rating = 95.8,
            releaseDateEpochSeconds = 1431993600L,
            summary = "The Witcher: Wild Hunt is a story-driven open world RPG set in a fantasy universe.",
            genres = listOf("Role-playing (RPG)", "Adventure"),
            platforms = listOf("PC", "PlayStation 5", "Xbox Series X|S", "Nintendo Switch")
        ),
        GameDto(
            id = 119133L,
            name = "Elden Ring",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg",
            rating = 95.2,
            releaseDateEpochSeconds = 1645747200L,
            summary = "Elden Ring is an action-RPG developed by FromSoftware, set in the Lands Between.",
            genres = listOf("Role-playing (RPG)", "Action"),
            platforms = listOf("PC", "PlayStation 5", "Xbox Series X|S")
        ),
        GameDto(
            id = 125174L,
            name = "Baldur's Gate 3",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co670x.jpg",
            rating = 96.1,
            releaseDateEpochSeconds = 1691020800L,
            summary = "An ancient evil has returned to Baldur's Gate, intent on devouring it from the inside out.",
            genres = listOf("Role-playing (RPG)", "Strategy", "Turn-based strategy (TBS)"),
            platforms = listOf("PC", "PlayStation 5", "Xbox Series X|S", "Mac")
        ),
        GameDto(
            id = 112875L,
            name = "God of War Ragnarök",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5s5v.jpg",
            rating = 93.7,
            releaseDateEpochSeconds = 1667952000L,
            summary = "Kratos and Atreus must journey to each of the Nine Realms in search of answers.",
            genres = listOf("Action", "Adventure", "Hack and slash/Beat 'em up"),
            platforms = listOf("PlayStation 4", "PlayStation 5", "PC")
        ),
        GameDto(
            id = 1877L,
            name = "Cyberpunk 2077",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co741v.jpg",
            rating = 87.4,
            releaseDateEpochSeconds = 1607558400L,
            summary = "Cyberpunk 2077 is an open-world, action-adventure RPG set in Night City.",
            genres = listOf("Role-playing (RPG)", "Shooter", "Action"),
            platforms = listOf("PC", "PlayStation 5", "Xbox Series X|S")
        ),
        GameDto(
            id = 119277L,
            name = "The Legend of Zelda: Tears of the Kingdom",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5vmg.jpg",
            rating = 95.5,
            releaseDateEpochSeconds = 1683849600L,
            summary = "An epic adventure across the land and skies of Hyrule awaits in Tears of the Kingdom.",
            genres = listOf("Adventure", "Action", "Puzzle"),
            platforms = listOf("Nintendo Switch")
        ),
        GameDto(
            id = 9927L,
            name = "Hollow Knight",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co93k6.jpg",
            rating = 91.8,
            releaseDateEpochSeconds = 1487894400L,
            summary = "Forge your own path in Hollow Knight! An epic action adventure through an insect kingdom.",
            genres = listOf("Platform", "Adventure", "Indie"),
            platforms = listOf("PC", "PlayStation 4", "Xbox One", "Nintendo Switch")
        ),
        GameDto(
            id = 1009L,
            name = "The Last of Us Part I",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5xex.jpg",
            rating = 92.4,
            releaseDateEpochSeconds = 1662076800L,
            summary = "Joel is hired to smuggle 14-year-old Ellie out of a military quarantine zone.",
            genres = listOf("Shooter", "Adventure", "Action"),
            platforms = listOf("PlayStation 5", "PC")
        ),
        GameDto(
            id = 25076L,
            name = "Red Dead Redemption 2",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1q1f.jpg",
            rating = 96.0,
            releaseDateEpochSeconds = 1540512000L,
            summary = "America, 1899. Arthur Morgan and the Van der Linde gang are outlaws on the run.",
            genres = listOf("Shooter", "Role-playing (RPG)", "Adventure"),
            platforms = listOf("PC", "PlayStation 4", "Xbox One")
        ),
        GameDto(
            id = 7331L,
            name = "Portal 2",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1rs4.jpg",
            rating = 94.6,
            releaseDateEpochSeconds = 1303171200L,
            summary = "Sequel to the acclaimed Portal (2007), Portal 2 pits the protagonist against a corrupt AI.",
            genres = listOf("Shooter", "Platform", "Puzzle"),
            platforms = listOf("PC", "PlayStation 3", "Xbox 360", "Nintendo Switch")
        )
    )

    override suspend fun getTopRatedGames(limit: Int, offset: Int): List<GameDto> {
        val safeOffset = offset.coerceAtLeast(0)
        if (safeOffset >= mockGames.size) return emptyList()
        return mockGames.drop(safeOffset).take(limit.coerceIn(1, 30))
    }

    override suspend fun searchGames(query: String, limit: Int, offset: Int): List<GameDto> {
        val filtered = mockGames.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.genres.any { genre -> genre.contains(query, ignoreCase = true) }
        }
        val safeOffset = offset.coerceAtLeast(0)
        if (safeOffset >= filtered.size) return emptyList()
        return filtered.drop(safeOffset).take(limit.coerceIn(1, 30))
    }

    override suspend fun getGameDetails(id: Long): GameDto {
        return mockGames.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Mock game with id $id not found")
    }
}
