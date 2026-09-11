package io.github.typenil.gametracker.devtools

/**
 * Prepared library shapes the developer tools can materialise.
 *
 * [syntheticBlock] gives every preset its own id block so presets can be merged without
 * overwriting each other, and so re-running the *same* preset is the only way an id is reused.
 */
internal enum class DevSeedPreset(val defaultCount: Int, val syntheticBlock: Long) {
    REALISTIC(20, 1_000L),
    COVERAGE(12, 2_000L),
    EDGE(7, 3_000L),
    STRESS(150, 4_000L),
    NOTIFICATIONS(5, 5_000L);

    /** Only these presets are built from the app's catalog source; the rest are generated in full. */
    val usesCatalog: Boolean
        get() = this == REALISTIC || this == COVERAGE

    companion object {
        const val MIN_COUNT = 1
        const val MAX_COUNT = 500

        fun parse(raw: String?): DevSeedPreset? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

        fun clampCount(count: Int): Int = count.coerceIn(MIN_COUNT, MAX_COUNT)
    }
}
