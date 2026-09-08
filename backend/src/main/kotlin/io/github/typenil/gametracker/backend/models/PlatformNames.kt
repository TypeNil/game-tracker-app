package io.github.typenil.gametracker.backend.models

const val IGDB_PC_PLATFORM = "PC (Microsoft Windows)"

internal fun displayPlatformName(name: String?, abbreviation: String?): String? {
    val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
    val trimmedAbbr = abbreviation?.trim()?.takeIf { it.isNotEmpty() }
    if (trimmedAbbr == "PC" || trimmedName.equals(IGDB_PC_PLATFORM, ignoreCase = true)) {
        return "PC"
    }
    return trimmedName ?: trimmedAbbr
}

internal fun canonicalPlatformName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.equals("PC", ignoreCase = true) || trimmed.equals(IGDB_PC_PLATFORM, ignoreCase = true)) {
        return IGDB_PC_PLATFORM
    }
    return trimmed
}
