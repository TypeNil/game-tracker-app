package com.gametracker.backend.models

internal fun displayPlatformName(name: String?, abbreviation: String?): String? {
    val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
    val trimmedAbbr = abbreviation?.trim()?.takeIf { it.isNotEmpty() }
    if (trimmedAbbr == "PC" || trimmedName.equals("PC (Microsoft Windows)", ignoreCase = true)) {
        return "PC"
    }
    return trimmedName ?: trimmedAbbr
}
