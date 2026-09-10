package io.github.typenil.gametracker.devtools

import io.github.typenil.gametracker.core.model.Game
import kotlin.random.Random

/**
 * Builds the library a preset describes.
 *
 * Pure by construction: the catalog is fetched by the caller and the clock is a parameter, so the
 * same request always produces the same library and the whole generator is unit-testable without
 * Android, a database, or a network.
 */
internal object DevSeedGenerator {

    fun generate(request: DevSeedRequest, catalog: List<Game>, nowEpochSeconds: Long): DevSeed {
        val random = Random(request.seed)
        val items = when (request.preset) {
            DevSeedPreset.REALISTIC ->
                DevCatalogSeed.realistic(catalog, request.count, random, nowEpochSeconds)

            DevSeedPreset.COVERAGE ->
                DevCatalogSeed.coverage(catalog, request.count, random, nowEpochSeconds)

            DevSeedPreset.EDGE -> DevSyntheticSeed.edge(nowEpochSeconds)
            DevSeedPreset.STRESS -> DevSyntheticSeed.stress(request.count, random, nowEpochSeconds)
            DevSeedPreset.NOTIFICATIONS -> DevSyntheticSeed.notifications(nowEpochSeconds)
        }
        return DevSeed(items = items, syntheticItems = items.filter { it.game.id >= SYNTHETIC_ID_BASE })
    }
}
