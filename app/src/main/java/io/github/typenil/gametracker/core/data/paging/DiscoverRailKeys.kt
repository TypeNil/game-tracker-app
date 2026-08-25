package io.github.typenil.gametracker.core.data.paging

object DiscoverRailKeys {
    private val types = listOf("visits", "playing", "wanted", "upcoming", "twitch")

    fun all(): List<String> = types.map(GameQueryKey::popular)
}
