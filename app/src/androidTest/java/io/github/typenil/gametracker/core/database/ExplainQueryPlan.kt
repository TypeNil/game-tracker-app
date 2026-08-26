package io.github.typenil.gametracker.core.database

import androidx.room.RoomDatabase

private val NAMED_BIND = Regex(":\\w+")

internal fun RoomDatabase.explainQueryPlan(queryConst: String, vararg bindArgs: Any): String {
    val sql = NAMED_BIND.replace(queryConst, "?")
    return query("EXPLAIN QUERY PLAN $sql", arrayOf(*bindArgs)).use { cursor ->
        buildString {
            while (cursor.moveToNext()) {
                appendLine(cursor.getString(cursor.columnCount - 1))
            }
        }
    }
}
