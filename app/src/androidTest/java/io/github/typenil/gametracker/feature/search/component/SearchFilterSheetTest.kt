package io.github.typenil.gametracker.feature.search.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.theme.GameTrackerTheme
import io.github.typenil.gametracker.feature.search.SearchFilters
import io.github.typenil.gametracker.feature.search.SearchSortOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class SearchFilterSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun queryPresent_disablesNonRelevanceSorts_andApplyKeepsStoredSort() {
        var applied: SearchFilters? = null
        val initial = SearchFilters(sort = SearchSortOption.RATING_DESC)

        composeTestRule.setContent {
            GameTrackerTheme {
                SearchFilterSheet(
                    initialFilters = initial,
                    onDismiss = {},
                    onApply = { applied = it },
                    queryPresent = true,
                )
            }
        }

        val context = composeTestRule.activity
        val helper = context.getString(R.string.search_sort_text_uses_relevance)
        val rating = context.getString(R.string.search_sort_rating_desc)
        val relevance = context.getString(R.string.search_sort_relevance)
        val apply = context.getString(R.string.search_filter_apply)

        composeTestRule.onNodeWithText(helper).assertIsDisplayed()
        composeTestRule.onNodeWithText(rating).assertIsNotEnabled()
        composeTestRule.onNodeWithText(relevance).assertIsSelected()
        composeTestRule.onNodeWithText(relevance).performClick()
        composeTestRule.onNodeWithText(apply).performClick()

        assertEquals(SearchSortOption.RATING_DESC, applied?.sort)
    }

    @Test
    fun queryAbsent_ratingSortChipIsEnabledAndSelectable() {
        var applied: SearchFilters? = null

        composeTestRule.setContent {
            GameTrackerTheme {
                SearchFilterSheet(
                    initialFilters = SearchFilters(),
                    onDismiss = {},
                    onApply = { applied = it },
                    queryPresent = false,
                )
            }
        }

        val context = composeTestRule.activity
        val rating = context.getString(R.string.search_sort_rating_desc)
        val apply = context.getString(R.string.search_filter_apply)

        composeTestRule.onNodeWithText(rating).assertIsEnabled()
        composeTestRule.onNodeWithText(rating).performClick()
        composeTestRule.onNodeWithText(rating).assertIsSelected()
        composeTestRule.onNodeWithText(apply).performClick()

        assertEquals(SearchSortOption.RATING_DESC, applied?.sort)
    }
}
