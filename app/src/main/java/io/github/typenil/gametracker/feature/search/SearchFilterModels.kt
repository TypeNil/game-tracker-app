package io.github.typenil.gametracker.feature.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.GameSearchQuery
import java.io.Serializable
import java.time.Clock
import java.time.Year
import java.util.Locale

enum class SearchSortOption(
    val wireSort: String?,
    @param:StringRes val labelRes: Int
) {
    RELEVANCE(null, R.string.search_sort_relevance),
    RATING_DESC("rating", R.string.search_sort_rating_desc),
    RELEASE_DATE_DESC("first_release_date", R.string.search_sort_date_desc),
    RELEASE_DATE_ASC("first_release_date_asc", R.string.search_sort_date_asc),
    NAME_ASC("name", R.string.search_sort_name_asc);
}

/**
 * Release year filter options calculated dynamically relative to the current UTC clock year.
 */
enum class ReleaseYearFilter(@param:StringRes val labelRes: Int) {
    ALL(R.string.search_filter_year_all),
    THIS_YEAR(R.string.search_filter_year_this_year),
    LAST_YEAR(R.string.search_filter_year_last_year),
    LAST_3_YEARS(R.string.search_filter_year_last_3),
    Y2010_2019(R.string.search_filter_year_2010s),
    RETRO(R.string.search_filter_year_retro);

    fun toYearRange(clockYear: Int = Year.now(Clock.systemUTC()).value): Pair<Int?, Int?> =
        when (this) {
            ALL -> null to null
            THIS_YEAR -> clockYear to clockYear
            LAST_YEAR -> (clockYear - 1) to (clockYear - 1)
            LAST_3_YEARS -> (clockYear - 2) to clockYear
            Y2010_2019 -> YEAR_2010 to YEAR_2019
            RETRO -> YEAR_RETRO_MIN to YEAR_RETRO_MAX
        }


    companion object {
        private const val YEAR_2010 = 2010
        private const val YEAR_2019 = 2019
        private const val YEAR_RETRO_MIN = 1970
        private const val YEAR_RETRO_MAX = 2009
    }
}

/**
 * Minimum rating filter options (IGDB 0-100 scale).
 */
enum class MinRatingFilter(
    val minRating: Int?,
    @param:StringRes val labelRes: Int
) {
    ANY(null, R.string.search_filter_rating_any),
    R70(70, R.string.search_filter_rating_70),
    R80(80, R.string.search_filter_rating_80),
    R90(90, R.string.search_filter_rating_90);
}

/**
 * Canonical IGDB wire names for genres.
 */
object SearchGenreCatalog {
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
        "Card & Board Game"
    )
}

/**
 * Expands a [PlatformFamily] into its concrete IGDB platform name strings.
 */
fun PlatformFamily.toIgdbNames(): List<String> = when (this) {
    PlatformFamily.PLAYSTATION -> listOf(
        "PlayStation 5", "PlayStation 4", "PlayStation 3",
        "PlayStation 2", "PlayStation", "PlayStation Vita", "PSP"
    )
    PlatformFamily.XBOX -> listOf(
        "Xbox Series X|S", "Xbox One", "Xbox 360", "Xbox"
    )
    PlatformFamily.NINTENDO -> listOf(
        "Nintendo Switch", "Nintendo Switch 2", "Wii U", "Wii",
        "Nintendo 3DS", "Nintendo DS", "Nintendo 64", "SNES", "NES"
    )
    PlatformFamily.PC -> listOf(
        "PC (Microsoft Windows)", "Mac", "Linux", "SteamOS"
    )
}

/**
 * Immutable filter state for the Search screen.
 */
@Immutable
data class SearchFilters(
    val genres: Set<String> = emptySet(),
    val platforms: Set<PlatformFamily> = emptySet(),
    val releaseYear: ReleaseYearFilter = ReleaseYearFilter.ALL,
    val minRating: MinRatingFilter = MinRatingFilter.ANY,
    val sort: SearchSortOption = SearchSortOption.RELEVANCE,
) {
    val hasConstraints: Boolean
        get() = genres.isNotEmpty() ||
            platforms.isNotEmpty() ||
            releaseYear != ReleaseYearFilter.ALL ||
            minRating != MinRatingFilter.ANY

    fun activeConstraintsCount(): Int {
        var count = 0
        if (genres.isNotEmpty()) count += genres.size
        if (platforms.isNotEmpty()) count += platforms.size
        if (releaseYear != ReleaseYearFilter.ALL) count += 1
        if (minRating != MinRatingFilter.ANY) count += 1
        return count
    }

    fun toDomainQuery(text: String): GameSearchQuery {
        val (minY, maxY) = releaseYear.toYearRange()
        val wireSort = if (text.isNotBlank()) null else sort.wireSort
        return GameSearchQuery(
            query = text,
            genres = genres.sorted(),
            platforms = platforms.flatMap { it.toIgdbNames() }.sorted(),
            minRating = minRating.minRating,
            minYear = minY,
            maxYear = maxY,
            sort = wireSort,
        )
    }

    companion object {
        val Empty = SearchFilters()

        val Saver: Saver<SearchFilters, *> = listSaver(
            save = { filters ->
                listOf(
                    filters.genres.toList(),
                    filters.platforms.map { it.name },
                    filters.releaseYear.name,
                    filters.minRating.name,
                    filters.sort.name,
                )
            },
            restore = { list ->
                @Suppress("UNCHECKED_CAST")
                val genres = (list.getOrNull(0) as? List<String>)?.toSet().orEmpty()
                @Suppress("UNCHECKED_CAST")
                val platformNames = (list.getOrNull(1) as? List<String>).orEmpty()
                val platforms = platformNames.mapNotNull { name ->
                    PlatformFamily.entries.firstOrNull { it.name == name }
                }.toSet()
                val yearName = list.getOrNull(2) as? String
                val year = ReleaseYearFilter.entries.firstOrNull { it.name == yearName } ?: ReleaseYearFilter.ALL
                val ratingName = list.getOrNull(3) as? String
                val rating = MinRatingFilter.entries.firstOrNull { it.name == ratingName } ?: MinRatingFilter.ANY
                val sortName = list.getOrNull(4) as? String
                val sort = SearchSortOption.entries.firstOrNull { it.name == sortName } ?: SearchSortOption.RELEVANCE
                SearchFilters(genres, platforms, year, rating, sort)
            }
        )
    }
}

/**
 * Quick search preset for 1-tap filtering from Idle state.
 */
sealed interface QuickSearchPreset {
    data class Genre(val name: String) : QuickSearchPreset
    data class Platform(val family: PlatformFamily) : QuickSearchPreset
    data object Rating80 : QuickSearchPreset
}

/**
 * Serializable snapshot of search filters for atomic SavedStateHandle persistence.
 */
data class SearchFiltersSnapshot(
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val year: String = ReleaseYearFilter.ALL.name,
    val rating: String = MinRatingFilter.ANY.name,
    val sort: String = SearchSortOption.RELEVANCE.name,
) : Serializable {
    fun toDomainFilters(): SearchFilters {
        val platformSet = platforms.mapNotNull { name ->
            PlatformFamily.entries.firstOrNull { it.name == name }
        }.toSet()
        val yearFilter = ReleaseYearFilter.entries.firstOrNull { it.name == year } ?: ReleaseYearFilter.ALL
        val ratingFilter = MinRatingFilter.entries.firstOrNull { it.name == rating } ?: MinRatingFilter.ANY
        val sortOption = SearchSortOption.entries.firstOrNull { it.name == sort } ?: SearchSortOption.RELEVANCE
        return SearchFilters(
            genres = genres.toSet(),
            platforms = platformSet,
            releaseYear = yearFilter,
            minRating = ratingFilter,
            sort = sortOption,
        )
    }
}

fun SearchFilters.toSnapshot(): SearchFiltersSnapshot = SearchFiltersSnapshot(
    genres = genres.toList(),
    platforms = platforms.map { it.name },
    year = releaseYear.name,
    rating = minRating.name,
    sort = sort.name,
)

/**
 * Client-side re-sorting of search hits when IGDB text search engine omits sorting.
 */
fun List<Game>.applyDisplaySort(sort: SearchSortOption, qPresent: Boolean): List<Game> {
    if (!qPresent || sort == SearchSortOption.RELEVANCE) return this
    return when (sort) {
        SearchSortOption.RATING_DESC -> sortedWith(
            compareByDescending<Game> { it.rating ?: -1.0 }.thenBy { it.name }
        )
        SearchSortOption.RELEASE_DATE_DESC -> sortedWith(
            compareByDescending<Game> { it.releaseDateEpochSeconds ?: Long.MIN_VALUE }.thenBy { it.name }
        )
        SearchSortOption.RELEASE_DATE_ASC -> sortedWith(
            compareBy<Game> { it.releaseDateEpochSeconds ?: Long.MAX_VALUE }.thenBy { it.name }
        )
        SearchSortOption.NAME_ASC -> sortedBy { it.name.lowercase(Locale.ROOT) }
        SearchSortOption.RELEVANCE -> this
    }
}
