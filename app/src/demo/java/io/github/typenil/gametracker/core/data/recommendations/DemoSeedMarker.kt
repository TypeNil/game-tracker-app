package io.github.typenil.gametracker.core.data.recommendations

/**
 * Remembers that the demo flavor's first-run library seeding has been resolved for this install.
 *
 * Only the demo flavor seeds a starter library, and only once: after the marker is set, emptying the
 * library in the app (or from the developer tools) leaves it empty instead of resurrecting the seed.
 * The live flavor never seeds and has no implementation of this.
 *
 * Implementations deliberately degrade instead of failing. The flag guards a demo convenience, and its
 * worst case is one redundant seed attempt on the next launch — the seed writes are upserts, so a repeat
 * converges. Throwing instead would abort `DiscoverViewModel`'s seeding coroutine before the first rail
 * loads, turning a cosmetic concern into a broken Discover screen.
 */
interface DemoSeedMarker {
    suspend fun isResolved(): Boolean

    suspend fun markResolved()
}
