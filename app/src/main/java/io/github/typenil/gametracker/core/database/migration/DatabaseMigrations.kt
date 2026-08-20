package io.github.typenil.gametracker.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for Room [io.github.typenil.gametracker.core.database.GameTrackerDatabase].
 */
object DatabaseMigrations {

    /**
     * Migration from Room schema version 1 to 2:
     * 1. Appends `hoursPlayed` column with default value 0 to `library_entries`.
     * 2. Deduplicates any overlapping positions in `search_results` prior to applying UNIQUE constraint.
     * 3. Recreates index on `search_results (query, position)` as UNIQUE.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Add hoursPlayed column to library_entries (appended at the end of table)
            db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `hoursPlayed` INTEGER NOT NULL DEFAULT 0")

            // 2. Deduplicate search_results to prevent constraint crash on legacy data
            db.execSQL(
                """
                DELETE FROM `search_results`
                WHERE rowid NOT IN (
                    SELECT MIN(rowid) FROM `search_results` GROUP BY `query`, `position`
                )
                """.trimIndent()
            )

            // 3. Re-create index on search_results(query, position) as UNIQUE
            db.execSQL("DROP INDEX IF EXISTS `index_search_results_query_position`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_search_results_query_position` ON `search_results` (`query`, `position`)"
            )
        }
    }
}
