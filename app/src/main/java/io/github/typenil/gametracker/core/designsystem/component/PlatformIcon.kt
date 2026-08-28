package io.github.typenil.gametracker.core.designsystem.component

import java.util.Locale
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R

enum class PlatformFamily(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
) {
    PLAYSTATION(R.drawable.ic_platform_playstation, R.string.platform_playstation),
    XBOX(R.drawable.ic_platform_xbox, R.string.platform_xbox),
    NINTENDO(R.drawable.ic_platform_nintendo, R.string.platform_nintendo),
    PC(R.drawable.ic_platform_pc, R.string.platform_pc),
}

fun resolvePlatformFamilies(platforms: List<String>): List<PlatformFamily> {
    if (platforms.isEmpty()) return emptyList()
    val present = BooleanArray(PlatformFamily.entries.size)
    for (raw in platforms) {
        val family = familyOf(raw.trim()) ?: continue
        present[family.ordinal] = true
    }
    return PlatformFamily.entries.filter { present[it.ordinal] }
}

private fun familyOf(platform: String): PlatformFamily? {
    if (platform.isEmpty()) return null
    val p = platform.lowercase(Locale.ROOT)
    return when {
        p.contains("playstation") ||
            (p.startsWith("ps") && p.any(Char::isDigit)) -> PlatformFamily.PLAYSTATION
        p.contains("xbox") -> PlatformFamily.XBOX
        p.contains("nintendo") || p.contains("switch") -> PlatformFamily.NINTENDO
        p == "pc" || p.contains("windows") || p == "mac" ||
            p.contains("linux") || p.contains("steam") -> PlatformFamily.PC
        else -> null
    }
}

@Composable
fun PlatformIconsRow(
    platforms: List<PlatformFamily>,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (platforms.isEmpty()) return
    val labels = platforms.map { stringResource(it.labelRes) }
    val label = labels.joinToString(", ")
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        platforms.forEach { family ->
            Icon(
                painter = painterResource(family.iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
