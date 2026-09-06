package io.github.typenil.gametracker.core.designsystem.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GtDimens {
    val Gutter = 16.dp
    val Card = 12.dp
    val Empty = 24.dp
    val BottomBarHeight = 80.dp
}

/**
 * Calculates the total inset-aware height reserved for the overlay navigation bar,
 * combining Material 3 bottom bar content height (80.dp) with system navigation bar insets.
 */
@Composable
fun topLevelBottomInset(): Dp {
    val systemBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    return GtDimens.BottomBarHeight + systemBottom
}
