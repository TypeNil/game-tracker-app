package io.github.typenil.gametracker.core.model

/**
 * Shared notes cap for library edits. UI and storage must use the same limit
 * so the editor never accepts text that the repository would silently drop.
 */
object LibraryNotes {
    const val MAX_CODE_POINTS = 500

    fun codePointCount(text: String): Int = text.codePointCount(0, text.length)

    fun isWithinLimit(text: String): Boolean = codePointCount(text) <= MAX_CODE_POINTS

    fun clamp(text: String): String {
        if (isWithinLimit(text)) return text
        val endIndex = text.offsetByCodePoints(0, MAX_CODE_POINTS)
        return text.substring(0, endIndex)
    }
}
