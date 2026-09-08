package io.github.typenil.gametracker.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.feature.library.component.LIBRARY_SKELETON_CARD_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_SKELETON_HERO_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LIBRARY_SKELETON_META_TEST_TAG
import io.github.typenil.gametracker.feature.library.component.LibraryCardSkeleton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryCardSkeletonTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun skeletonCard_heroIsWidescreenWithMetaBelow() {
        composeTestRule.setContent {
            GameTrackerTheme {
                Box(Modifier.height(900.dp)) {
                    LibraryCardSkeleton(modifier = Modifier.fillMaxSize())
                }
            }
        }

        val hero = composeTestRule.onAllNodesWithTag(LIBRARY_SKELETON_HERO_TEST_TAG)
            .onFirst()
            .getUnclippedBoundsInRoot()
        val card = composeTestRule.onAllNodesWithTag(LIBRARY_SKELETON_CARD_TEST_TAG)
            .onFirst()
            .getUnclippedBoundsInRoot()
        val width = hero.right - hero.left
        val height = hero.bottom - hero.top
        val ratio = width / height
        assertTrue("Hero should be ~16:9, was $ratio", ratio in 1.6f..2.0f)
        assertTrue(
            "Meta row should sit below the hero, gap was ${card.bottom - hero.bottom}",
            card.bottom > hero.bottom + 48.dp,
        )
        val meta = composeTestRule.onAllNodesWithTag(LIBRARY_SKELETON_META_TEST_TAG)
            .onFirst()
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Meta strip should be at least 48.dp, was ${meta.bottom - meta.top}",
            meta.bottom - meta.top >= 47.9.dp,
        )
    }
}
