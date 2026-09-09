package io.github.typenil.gametracker.navigation

import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import android.net.Uri
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.Navigator
import io.github.typenil.gametracker.feature.details.navigation.GameDetailsKey
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey
import io.github.typenil.gametracker.feature.search.navigation.SearchKey
import io.github.typenil.gametracker.feature.settings.navigation.SettingsKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppNavigationMotionTest {

    @Navigator.Name("test")
    private class TestNavigator : Navigator<NavDestination>() {
        override fun createDestination(): NavDestination = NavDestination(this)
    }

    private val navigator = TestNavigator()

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true) {
            every { query } returns null
            every { isHierarchical } returns true
        }
        every { Uri.parse(any()) } returns mockUri
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun nullDestination_isNotTopLevel() {
        val destination: NavDestination? = null
        assertFalse(destination.isTopLevelDestination())
    }

    @Test
    fun discoverDestination_isTopLevel() {
        val destination = NavDestinationBuilder(navigator, route = DiscoverKey::class, typeMap = emptyMap()).build()
        assertTrue(destination.isTopLevelDestination())
    }

    @Test
    fun libraryDestination_isTopLevel() {
        val destination = NavDestinationBuilder(navigator, route = LibraryKey::class, typeMap = emptyMap()).build()
        assertTrue(destination.isTopLevelDestination())
    }

    @Test
    fun searchDestination_isTopLevel() {
        val destination = NavDestinationBuilder(navigator, route = SearchKey::class, typeMap = emptyMap()).build()
        assertTrue(destination.isTopLevelDestination())
    }

    @Test
    fun subScreenDestinations_areNotTopLevel() {
        val gameDetails = NavDestinationBuilder(navigator, route = GameDetailsKey::class, typeMap = emptyMap()).build()
        assertFalse(gameDetails.isTopLevelDestination())

        val settings = NavDestinationBuilder(navigator, route = SettingsKey::class, typeMap = emptyMap()).build()
        assertFalse(settings.isTopLevelDestination())
    }

    @Test
    fun motionConstants_adhereToMaterial3Durations() {
        // Forward enter should give more time to perceive new content than exit
        assertTrue(AppNavigationMotion.DURATION_ENTER_MS > AppNavigationMotion.DURATION_EXIT_MS)
        assertEquals(350, AppNavigationMotion.DURATION_ENTER_MS)
        assertEquals(300, AppNavigationMotion.DURATION_EXIT_MS)

        // Tab crossfade must be brisk and snappy (<= 200ms)
        assertTrue(AppNavigationMotion.DURATION_TAB_CROSSFADE_MS <= 200)
        assertEquals(180, AppNavigationMotion.DURATION_TAB_CROSSFADE_MS)

        // Parallax factor should be subtle (between 10% and 25%)
        assertTrue(AppNavigationMotion.PARALLAX_RECEDE_FACTOR in 0.10f..0.25f)
        assertEquals(0.15f, AppNavigationMotion.PARALLAX_RECEDE_FACTOR, 0.001f)
    }

    @Test
    fun bottomBarOverlayConstants_adhereToMaterial3Spec() {
        assertEquals(80.dp, GtDimens.BottomBarHeight)
        assertTrue(GtDimens.BottomBarHeight > GtDimens.Gutter)
    }
}
