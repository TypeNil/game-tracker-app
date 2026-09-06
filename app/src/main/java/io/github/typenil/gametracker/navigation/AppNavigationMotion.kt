package io.github.typenil.gametracker.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import io.github.typenil.gametracker.feature.discover.navigation.DiscoverKey
import io.github.typenil.gametracker.feature.library.navigation.LibraryKey

/**
 * Material 3 Motion specifications and transition transitions for root navigation destinations.
 */
internal object AppNavigationMotion {
    const val DURATION_ENTER_MS = 350
    const val DURATION_EXIT_MS = 300
    const val DURATION_FADE_IN_MS = 220
    const val DURATION_FADE_OUT_MS = 200
    const val DURATION_TAB_CROSSFADE_MS = 180

    // Material 3 Emphasized Decelerate for incoming forward/pop elements
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    // Material 3 Emphasized Accelerate for outgoing elements
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // Parallax depth offset factor for the receding background screen
    const val PARALLAX_RECEDE_FACTOR = 0.15f
}

/**
 * Determines whether a destination is one of the top-level bottom navigation tabs.
 */
internal fun NavDestination?.isTopLevelDestination(): Boolean {
    if (this == null) return false
    return hasRoute<DiscoverKey>() || hasRoute<LibraryKey>()
}

/**
 * Global enter transition for [androidx.navigation.compose.NavHost].
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.appNavEnterTransition(): EnterTransition {
    val fromTopLevel = initialState.destination.isTopLevelDestination()
    val toTopLevel = targetState.destination.isTopLevelDestination()

    return if (fromTopLevel && toTopLevel) {
        // Switching between top-level tabs (Discover <-> Library): instant clean crossfade
        fadeIn(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_TAB_CROSSFADE_MS,
                easing = LinearOutSlowInEasing
            )
        )
    } else {
        // Forward navigation to a sub-screen (Details, Search, Settings): slide in from right with fade
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_ENTER_MS,
                easing = AppNavigationMotion.EmphasizedDecelerateEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_FADE_IN_MS,
                easing = LinearOutSlowInEasing
            )
        )
    }
}

/**
 * Global exit transition for [androidx.navigation.compose.NavHost].
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.appNavExitTransition(): ExitTransition {
    val fromTopLevel = initialState.destination.isTopLevelDestination()
    val toTopLevel = targetState.destination.isTopLevelDestination()

    return if (fromTopLevel && toTopLevel) {
        // Switching between top-level tabs: instant clean crossfade
        fadeOut(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_TAB_CROSSFADE_MS,
                easing = FastOutLinearInEasing
            )
        )
    } else {
        // Underneath screen recedes to the left with subtle parallax
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            targetOffset = { fullWidth -> (fullWidth * AppNavigationMotion.PARALLAX_RECEDE_FACTOR).toInt() },
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_EXIT_MS,
                easing = AppNavigationMotion.EmphasizedAccelerateEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_FADE_OUT_MS,
                easing = FastOutLinearInEasing
            )
        )
    }
}

/**
 * Global pop enter transition for [androidx.navigation.compose.NavHost] (returning to a screen).
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.appNavPopEnterTransition(): EnterTransition {
    val fromTopLevel = initialState.destination.isTopLevelDestination()
    val toTopLevel = targetState.destination.isTopLevelDestination()

    return if (fromTopLevel && toTopLevel) {
        fadeIn(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_TAB_CROSSFADE_MS,
                easing = LinearOutSlowInEasing
            )
        )
    } else {
        // Returning screen slides forward from receded parallax position
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            initialOffset = { fullWidth -> (fullWidth * AppNavigationMotion.PARALLAX_RECEDE_FACTOR).toInt() },
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_ENTER_MS,
                easing = AppNavigationMotion.EmphasizedDecelerateEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_FADE_IN_MS,
                easing = LinearOutSlowInEasing
            )
        )
    }
}

/**
 * Global pop exit transition for [androidx.navigation.compose.NavHost] (leaving a screen backwards).
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.appNavPopExitTransition(): ExitTransition {
    val fromTopLevel = initialState.destination.isTopLevelDestination()
    val toTopLevel = targetState.destination.isTopLevelDestination()

    return if (fromTopLevel && toTopLevel) {
        fadeOut(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_TAB_CROSSFADE_MS,
                easing = FastOutLinearInEasing
            )
        )
    } else {
        // Popping screen slides away to the right
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_EXIT_MS,
                easing = AppNavigationMotion.EmphasizedAccelerateEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AppNavigationMotion.DURATION_FADE_OUT_MS,
                easing = FastOutLinearInEasing
            )
        )
    }
}
