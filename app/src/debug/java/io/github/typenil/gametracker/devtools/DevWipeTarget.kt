package io.github.typenil.gametracker.devtools

/**
 * User state the developer tools can clear. Deliberately not "catalog cache": pull-to-refresh
 * already refetches it, and deleting `games` rows that `search_results` still references would only
 * manufacture foreign-key noise.
 */
internal enum class DevWipeTarget {
    LIBRARY,
    SEARCH_HISTORY,
    NOTIFICATION_LEDGER,

    /**
     * App database + user preferences + the debug BFF override. Not a `pm clear` equivalent:
     * WorkManager's own database, already-posted notifications and the in-memory details preview
     * cache survive it.
     */
    ALL;

    companion object {
        fun parse(raw: String?): DevWipeTarget? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
