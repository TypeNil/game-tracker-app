package io.github.typenil.gametracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCardTagsTest {

    @Test
    fun `selectCardTags interleaves one tag from each category before filling`() {
        val tags = selectCardTags(
            genres = listOf("RPG", "Adventure", "Strategy"),
            themes = listOf("Fantasy", "Open world"),
            platforms = listOf("PC", "PlayStation 5"),
            max = 4,
        )

        assertEquals(listOf("RPG", "Fantasy", "PC", "Adventure"), tags)
    }

    @Test
    fun `selectCardTags fills from remaining categories when one is empty`() {
        val tags = selectCardTags(
            genres = listOf("Shooter", "RPG"),
            themes = emptyList(),
            platforms = listOf("PC", "Xbox Series X"),
            max = 4,
        )

        assertEquals(listOf("Shooter", "PC", "RPG", "Xbox Series X"), tags)
    }

    @Test
    fun `selectCardTags skips blanks duplicates and stops at max`() {
        val tags = selectCardTags(
            genres = listOf(" RPG ", "RPG", ""),
            themes = listOf("Fantasy", "RPG"),
            platforms = listOf("PC"),
            max = 3,
        )

        assertEquals(listOf("RPG", "Fantasy", "PC"), tags)
    }

    @Test
    fun `Game cardTags uses genres themes and platforms`() {
        val game = Game(
            id = 1L,
            name = "The Witcher 3",
            genres = listOf("RPG", "Adventure"),
            themes = listOf("Fantasy"),
            platforms = listOf("PC"),
        )

        assertEquals(listOf("RPG", "Fantasy", "PC", "Adventure"), game.cardTags())
    }
}
