package io.github.typenil.gametracker.core.database.transaction

import androidx.room.withTransaction
import io.github.typenil.gametracker.core.database.GameTrackerDatabase

/**
 * Abstraction for executing database transactions within coroutines.
 * Decouples repository business logic from static Room extension functions,
 * enabling clean substitution with test doubles in JVM unit tests.
 */
interface TransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T
}

/**
 * Production implementation of [TransactionRunner] delegating to [GameTrackerDatabase.withTransaction].
 */
class RoomTransactionRunner(
    private val database: GameTrackerDatabase
) : TransactionRunner {
    override suspend fun <T> invoke(block: suspend () -> T): T {
        return database.withTransaction(block)
    }
}
