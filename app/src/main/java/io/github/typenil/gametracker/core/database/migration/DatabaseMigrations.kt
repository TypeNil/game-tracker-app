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

    /**
     * Migration from Room schema version 2 to 3: adds the `game_details` cache table.
     * New table only — no legacy data to deduplicate, no column-order concerns.
     * Column order matches the GameDetailsEntity property order (standard 4.3.8).
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `game_details` (
                `gameId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `coverUrl` TEXT,
                `rating` REAL,
                `totalRating` REAL,
                `totalRatingCount` INTEGER,
                `releaseDateEpochSeconds` INTEGER,
                `summary` TEXT,
                `url` TEXT,
                `genres` TEXT NOT NULL,
                `themes` TEXT NOT NULL,
                `gameModes` TEXT NOT NULL,
                `platforms` TEXT NOT NULL,
                `releaseDates` TEXT NOT NULL,
                `companies` TEXT NOT NULL,
                `screenshots` TEXT NOT NULL,
                `videos` TEXT NOT NULL,
                `similarGames` TEXT NOT NULL,
                `cachedAtEpochSeconds` INTEGER NOT NULL,
                PRIMARY KEY (`gameId`)
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration from Room schema version 3 to 4: adds the `notification_events` table for release deduplication.
     * Cascades delete when the parent game entity is deleted.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notification_events` (
                `eventKey` TEXT NOT NULL,
                `gameId` INTEGER NOT NULL,
                `eventType` TEXT NOT NULL,
                `releaseDateEpochSeconds` INTEGER,
                `notifiedAtEpochSeconds` INTEGER NOT NULL,
                PRIMARY KEY (`eventKey`),
                FOREIGN KEY (`gameId`) REFERENCES `games` (`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notification_events_gameId` ON `notification_events` (`gameId`)"
            )
        }
    }

    /** Adds optional details artwork and time-to-beat metadata. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `game_details` ADD COLUMN `artworkUrl` TEXT")
            db.execSQL("ALTER TABLE `game_details` ADD COLUMN `timeToBeatMainSeconds` INTEGER")
            db.execSQL("ALTER TABLE `game_details` ADD COLUMN `timeToBeatCompleteSeconds` INTEGER")
            db.execSQL("UPDATE `game_details` SET `cachedAtEpochSeconds` = 0")
        }
    }

    /**
     * Migration from Room schema version 5 to 6: adds the `search_history` table for persistent user search history.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_history` (
                `normalizedQuery` TEXT NOT NULL,
                `displayQuery` TEXT NOT NULL,
                `lastQueriedAtEpochSeconds` INTEGER NOT NULL,
                PRIMARY KEY (`normalizedQuery`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_search_history_lastQueriedAtEpochSeconds` " +
                    "ON `search_history` (`lastQueriedAtEpochSeconds`)"
            )
        }
    }
}
