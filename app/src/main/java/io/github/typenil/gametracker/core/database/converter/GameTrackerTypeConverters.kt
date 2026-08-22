package io.github.typenil.gametracker.core.database.converter

import androidx.room.TypeConverter
import io.github.typenil.gametracker.core.database.entity.CompanyColumn
import io.github.typenil.gametracker.core.database.entity.ReleaseDateColumn
import io.github.typenil.gametracker.core.database.entity.SimilarGameColumn
import io.github.typenil.gametracker.core.database.entity.VideoColumn
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for serializing complex data structures and enums into SQLite columns.
 * Decoding is strict by design (standard 4.3.4): a corrupted column must fail loudly
 * instead of silently swallowing into a default value.
 */
class GameTrackerTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private inline fun <reified T> encodeColumnList(value: List<T>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return json.encodeToString(value)
    }

    private inline fun <reified T> decodeColumnList(value: String?): List<T> {
        if (value.isNullOrBlank() || value == "[]") return emptyList()
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String = encodeColumnList(value)

    @TypeConverter
    fun toStringList(value: String?): List<String> = decodeColumnList(value)

    @TypeConverter
    fun fromLibraryStatus(status: LibraryStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toLibraryStatus(value: String?): LibraryStatus? {
        if (value.isNullOrBlank()) return null
        return LibraryStatus.valueOf(value)
    }
}

/**
 * TypeConverters for the JSON-encoded nested column types of the `game_details`
 * cache table (schema v3). Kept separate to hold the function count per class
 * under detekt's TooManyFunctions threshold.
 */
class GameDetailsColumnConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private inline fun <reified T> encodeColumnList(value: List<T>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return json.encodeToString(value)
    }

    private inline fun <reified T> decodeColumnList(value: String?): List<T> {
        if (value.isNullOrBlank() || value == "[]") return emptyList()
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun fromReleaseDateList(value: List<ReleaseDateColumn>?): String = encodeColumnList(value)

    @TypeConverter
    fun toReleaseDateList(value: String?): List<ReleaseDateColumn> = decodeColumnList(value)

    @TypeConverter
    fun fromCompanyList(value: List<CompanyColumn>?): String = encodeColumnList(value)

    @TypeConverter
    fun toCompanyList(value: String?): List<CompanyColumn> = decodeColumnList(value)

    @TypeConverter
    fun fromVideoList(value: List<VideoColumn>?): String = encodeColumnList(value)

    @TypeConverter
    fun toVideoList(value: String?): List<VideoColumn> = decodeColumnList(value)

    @TypeConverter
    fun fromSimilarGameList(value: List<SimilarGameColumn>?): String = encodeColumnList(value)

    @TypeConverter
    fun toSimilarGameList(value: String?): List<SimilarGameColumn> = decodeColumnList(value)
}
