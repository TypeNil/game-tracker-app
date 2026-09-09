package io.github.typenil.gametracker.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun isValidColdStart_acceptsActionAsThemeChip() {
        assertTrue(
            UserPreferences.isValidColdStart(
                tags = setOf("Role-playing (RPG)", "Action", "Adventure"),
                platforms = setOf("PC"),
            ),
        )
    }

    @Test
    fun isValidColdStart_rejectsUnknownTag() {
        assertFalse(
            UserPreferences.isValidColdStart(
                tags = setOf("Role-playing (RPG)", "Adventure", "Not A Genre"),
                platforms = setOf("PC"),
            ),
        )
    }
}
