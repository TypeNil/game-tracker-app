package io.github.typenil.gametracker.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VideogameAsset
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
import java.util.Locale

enum class PlatformFamily(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int,
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
        val family = resolvePlatformFamily(raw) ?: continue
        present[family.ordinal] = true
    }
    return PlatformFamily.entries.filter { present[it.ordinal] }
}

fun resolvePlatformFamily(platform: String): PlatformFamily? {
    if (platform.isEmpty()) return null
    val p = platform.lowercase(Locale.ROOT)
    return when {
        p.contains("playstation") ||
            (p.startsWith("ps") && p.any(Char::isDigit)) ||
            p == "psp" || p.contains("vita") -> PlatformFamily.PLAYSTATION
        p.contains("xbox") -> PlatformFamily.XBOX
        p.contains("nintendo") || p.contains("switch") || p.contains("wii") ||
            p.contains("game boy") || p.contains("gameboy") ||
            p == "3ds" || p.endsWith(" 3ds") || p == "nds" || p.contains("nintendo ds") ||
            p == "nes" || p.contains("entertainment system") ||
            p == "snes" || p.contains("super nintendo") || p.contains("n64") -> PlatformFamily.NINTENDO
        p == "pc" || p.contains("windows") || p == "mac" || p == "macintosh" ||
            p.contains("linux") || p.contains("steam") || p == "dos" || p.contains("ms-dos") -> PlatformFamily.PC
        else -> null
    }
}

/** Normalizes technical IGDB platform names to clean, readable user-facing strings. */
fun formatPlatformDisplayName(rawPlatform: String): String {
    val trimmed = rawPlatform.trim()
    if (trimmed.isEmpty()) return ""
    val lower = trimmed.lowercase(Locale.ROOT)
    return when {
        lower == "pc (microsoft windows)" || lower == "microsoft windows" || lower == "windows" -> "PC"
        lower == "mac os" || lower == "macintosh" || lower == "mac os x" -> "Mac"
        lower == "xbox series x|s" || lower == "xbox series x/s" -> "Xbox Series X|S"
        lower == "sega genesis / mega drive" || lower == "sega mega drive/genesis" -> "Sega Mega Drive"
        lower == "web browser" -> "Web"
        else -> trimmed
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

@Composable
fun PlatformIconView(
    platform: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val family = resolvePlatformFamily(platform)
    val p = platform.lowercase(Locale.ROOT)
    if (family != null) {
        Icon(
            painter = painterResource(family.iconRes),
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(iconSize),
        )
    } else {
        val vector = when {
            p.contains("android") || p.contains("ios") || p == "iphone" || p == "ipad" -> Icons.Filled.Smartphone
            p.contains("web") || p.contains("browser") -> Icons.Filled.Language
            else -> Icons.Filled.VideogameAsset
        }
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(iconSize),
        )
    }
}
