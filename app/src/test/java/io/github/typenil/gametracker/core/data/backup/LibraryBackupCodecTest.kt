package io.github.typenil.gametracker.core.data.backup

import io.github.typenil.gametracker.core.model.Game
import io.github.typenil.gametracker.core.model.LibraryEntry
import io.github.typenil.gametracker.core.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LibraryBackupCodecTest {

    @Test
    fun roundTrip_preservesUserStateAndGameSnapshot() {
        val backup = LibraryBackupFile(
            schemaVersion = LibraryBackupCodec.SCHEMA_VERSION,
            exportedAt = Instant.parse("2026-09-10T12:00:00Z"),
            items = listOf(sampleItem()),
        )

        val decoded = LibraryBackupCodec.decode(LibraryBackupCodec.encode(backup))

        assertTrue(decoded is LibraryBackupParseResult.Success)
        assertEquals(backup, (decoded as LibraryBackupParseResult.Success).file)
    }

    @Test
    fun decode_ignoresUnknownObjectFields() {
        val json = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-09-10T12:00:00Z",
              "extra": "forward-compat",
              "library": [
                {
                  "gameId": 1942,
                  "status": "PLAYING",
                  "favorite": true,
                  "hours": 40,
                  "releaseNotificationsEnabled": true,
                  "addedAtEpochSeconds": 10,
                  "updatedAtEpochSeconds": 20,
                  "surprise": true,
                  "game": {
                    "id": 1942,
                    "name": "The Witcher 3: Wild Hunt",
                    "genres": [],
                    "platforms": []
                  }
                }
              ]
            }
        """.trimIndent().toByteArray()

        val decoded = LibraryBackupCodec.decode(json)
        assertTrue(decoded is LibraryBackupParseResult.Success)
        val item = (decoded as LibraryBackupParseResult.Success).file.items.single()
        assertEquals(1942L, item.entry.gameId)
        assertEquals(LibraryStatus.PLAYING, item.entry.status)
        assertEquals("The Witcher 3: Wild Hunt", item.game.name)
    }

    @Test
    fun decode_rejectsUnsupportedSchema() {
        val json = """
            {"schemaVersion":2,"exportedAt":"2026-09-10T12:00:00Z","library":[]}
        """.trimIndent().toByteArray()

        val decoded = LibraryBackupCodec.decode(json)
        assertEquals(
            LibraryBackupError.UNSUPPORTED_SCHEMA,
            (decoded as LibraryBackupParseResult.Failure).error,
        )
    }

    @Test
    fun decode_rejectsDuplicateGameIds() {
        val item = encodedItemJson(1942)
        val json = """
            {"schemaVersion":1,"exportedAt":"2026-09-10T12:00:00Z","library":[$item,$item]}
        """.trimIndent().toByteArray()

        val decoded = LibraryBackupCodec.decode(json)
        assertEquals(
            LibraryBackupError.INVALID_DOCUMENT,
            (decoded as LibraryBackupParseResult.Failure).error,
        )
    }

    @Test
    fun decode_rejectsMismatchedGameId() {
        val json = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-09-10T12:00:00Z",
              "library": [${encodedItemJson(gameId = 1, snapshotId = 2)}]
            }
        """.trimIndent().toByteArray()

        val decoded = LibraryBackupCodec.decode(json)
        assertEquals(
            LibraryBackupError.INVALID_DOCUMENT,
            (decoded as LibraryBackupParseResult.Failure).error,
        )
    }

    @Test
    fun decode_rejectsUnknownAndLegacyStatus() {
        assertEquals(
            LibraryBackupError.INVALID_DOCUMENT,
            (LibraryBackupCodec.decode(statusJson("PLAN_TO_PLAY")) as LibraryBackupParseResult.Failure).error,
        )
        assertEquals(
            LibraryBackupError.INVALID_DOCUMENT,
            (LibraryBackupCodec.decode(statusJson("ARCHIVED")) as LibraryBackupParseResult.Failure).error,
        )
    }

    @Test
    fun decode_rejectsBlankNameNegativeHoursAndBadTimestamps() {
        assertEquals(
            LibraryBackupError.INVALID_DOCUMENT,
            (
                LibraryBackupCodec.decode(
                    encodedDocument(encodedItemJson(name = " ")),
                ) as LibraryBackupParseResult.Failure
                ).error,
        )
        assertEquals(
            LibraryBackupError.INVALID_DOCUMENT,
            (
                LibraryBackupCodec.decode(
                    encodedDocument(encodedItemJson(hours = -1)),
                ) as LibraryBackupParseResult.Failure
                ).error,
        )
        assertEquals(
            LibraryBackupError.INVALID_DOCUMENT,
            (
                LibraryBackupCodec.decode(
                    encodedDocument(encodedItemJson(addedAt = 20, updatedAt = 10)),
                ) as LibraryBackupParseResult.Failure
                ).error,
        )
    }

    @Test
    fun encode_usesFrozenV1StatusWireValues() {
        val json = LibraryBackupCodec.encode(
            LibraryBackupFile(
                schemaVersion = 1,
                exportedAt = Instant.parse("2026-09-10T12:00:00Z"),
                items = listOf(sampleItem(status = LibraryStatus.WISHLIST)),
            ),
        ).decodeToString()
        assertTrue(json.contains("\"status\": \"WISHLIST\""))
        assertTrue(!json.contains("PLAN_TO_PLAY"))
    }

    private fun sampleItem(
        status: LibraryStatus = LibraryStatus.PLAYING,
    ) = LibraryBackupItem(
        entry = LibraryEntry(
            gameId = 1942L,
            status = status,
            userRating = 9,
            userNotes = "notes",
            isFavorite = true,
            addedAtEpochSeconds = 10L,
            updatedAtEpochSeconds = 20L,
            hoursPlayed = 40,
            releaseNotificationsEnabled = true,
        ),
        game = Game(
            id = 1942L,
            name = "The Witcher 3: Wild Hunt",
            coverUrl = "https://example.test/cover.jpg",
            rating = 96.4,
            releaseDateEpochSeconds = 1431993600L,
            summary = "Open world RPG",
            genres = listOf("Role-playing (RPG)"),
            platforms = listOf("PC"),
        ),
    )

    private fun statusJson(status: String): ByteArray =
        encodedDocument(encodedItemJson(status = status))

    private fun encodedDocument(itemJson: String): ByteArray =
        """{"schemaVersion":1,"exportedAt":"2026-09-10T12:00:00Z","library":[$itemJson]}"""
            .toByteArray()

    private fun encodedItemJson(
        gameId: Long = 1942,
        snapshotId: Long = gameId,
        status: String = "PLAYING",
        name: String = "The Witcher 3: Wild Hunt",
        hours: Int = 0,
        addedAt: Long = 10,
        updatedAt: Long = 20,
    ): String = """
        {
          "gameId": $gameId,
          "status": "$status",
          "hours": $hours,
          "favorite": false,
          "releaseNotificationsEnabled": false,
          "addedAtEpochSeconds": $addedAt,
          "updatedAtEpochSeconds": $updatedAt,
          "game": {"id": $snapshotId, "name": "$name", "genres": [], "platforms": []}
        }
    """.trimIndent()
}
