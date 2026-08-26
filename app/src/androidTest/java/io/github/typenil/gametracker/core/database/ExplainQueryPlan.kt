package io.github.typenil.gametracker.core.database

import androidx.room.RoomDatabase

internal fun RoomDatabase.explainQueryPlan(queryConst: String, vararg bindArgs: Any): String {
    val sql = NAMED_BIND.replace(queryConst, "?")
    val cursor = query("EXPLAIN QUERY PLAN $sql", arrayOf(*bindArgs))
    val details = buildString {
        while (cursor.moveToNext()) {
            appendLine(cursor.getString(cursor.columnCount - 1))
        }
    }
    cursor.close()
    return details
}

private val NAMED_BIND = Regex(":\\w+")
