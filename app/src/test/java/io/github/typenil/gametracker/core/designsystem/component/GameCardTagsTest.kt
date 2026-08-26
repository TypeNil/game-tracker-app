package io.github.typenil.gametracker.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCardTagsTest {

    @Test
    fun `selectCardTags prefers two genres then one platform`() {
        val tags = selectCardTags(
            genres = listOf("RPG", "Adventure", "Strategy"),
            platforms = listOf("PC", "PlayStation 5"),
        )

        assertEquals(listOf("RPG", "Adventure", "PC"), tags)
    }

    @Test
    fun `selectCardTags uses platforms when genres are empty`() {
        val tags = selectCardTags(
            genres = emptyList(),
            platforms = listOf("PC", "Xbox Series X"),
        )

        assertEquals(listOf("PC"), tags)
    }

    @Test
    fun `selectCardTags skips blanks and duplicates`() {
        val tags = selectCardTags(
            genres = listOf(" RPG ", "RPG", "", "Adventure"),
            platforms = listOf("RPG", "PC"),
        )

        assertEquals(listOf("RPG", "Adventure", "PC"), tags)
    }
}
