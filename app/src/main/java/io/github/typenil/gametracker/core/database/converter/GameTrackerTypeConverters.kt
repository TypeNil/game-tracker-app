package io.github.typenil.gametracker.core.database.converter

import androidx.room.TypeConverter
import io.github.typenil.gametracker.core.model.LibraryStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for serializing complex data structures and enums into SQLite columns.
 */
class GameTrackerTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank() || value == "[]") return emptyList()
        return json.decodeFromString<List<String>>(value)
    }

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
