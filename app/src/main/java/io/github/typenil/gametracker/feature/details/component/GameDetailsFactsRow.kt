package io.github.typenil.gametracker.feature.details.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.formatPlatformDisplayName
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.GameDetails
import io.github.typenil.gametracker.core.model.GameReleaseDate
import io.github.typenil.gametracker.feature.details.PLATFORMS_PREVIEW_LIMIT_FULL
import io.github.typenil.gametracker.feature.details.PLATFORMS_PREVIEW_LIMIT_HALF
import io.github.typenil.gametracker.feature.details.displayDate
import io.github.typenil.gametracker.feature.details.formatGameModesPreview
import io.github.typenil.gametracker.feature.details.formatPlatformsPreview
import io.github.typenil.gametracker.feature.details.resolveFactsTopology

private val DETAILS_GUTTER = GtDimens.Gutter

/** Two-column summary cards; a lone leftover card spans the full row. */
@Composable
internal fun GameDetailsFactsRow(
    game: GameDetails,
    onPlatformsClick: () -> Unit,
    onGameModesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownDate = stringResource(R.string.details_date_unknown)
    val firstRelease = game.releaseDates.firstOrNull()
    val datedRelease = firstRelease?.takeIf {
        it.dateEpochSeconds != null || it.year != null
    }
    val releaseText = datedRelease?.displayDate(unknownDate)
        ?: game.releaseDateEpochSeconds?.let { epoch ->
            GameReleaseDate(
                platform = "",
                dateEpochSeconds = epoch,
            ).displayDate(unknownDate)
        }
        ?: unknownDate.takeIf { firstRelease != null }
    val mainHours = game.timeToBeatMainSeconds?.toDisplayHours()
    val topology = remember(releaseText, game.gameModes, game.platforms, mainHours) {
        resolveFactsTopology(
            hasRelease = releaseText != null,
            hasModes = game.gameModes.isNotEmpty(),
            hasPlatforms = game.platforms.isNotEmpty(),
            hasTime = mainHours != null,
        )
    }
    val cards = buildList {
        if (releaseText != null) {
            add(
                FactCardData(
                    testTag = "release",
                    icon = Icons.Filled.Event,
                    title = stringResource(R.string.details_card_release),
                    value = releaseText,
                    sub = firstRelease
                        ?.platform
                        ?.let(::formatPlatformDisplayName)
                        ?.takeIf(String::isNotBlank),
                )
            )
        }
        if (game.gameModes.isNotEmpty()) {
            val modesPreview = formatGameModesPreview(game.gameModes)
            val isClickable = game.gameModes.size > 1
            val subText = if (modesPreview.overflowCount > 0) {
                stringResource(R.string.details_more_count, modesPreview.overflowCount)
            } else {
                null
            }
            val a11yDesc = if (isClickable) {
                stringResource(R.string.details_modes_more_desc, game.gameModes.size)
            } else {
                null
            }
            add(
                FactCardData(
                    testTag = "modes",
                    icon = Icons.Filled.VideogameAsset,
                    title = stringResource(R.string.details_section_modes),
                    value = modesPreview.previewText,
                    sub = subText,
                    isClickable = isClickable,
                    onClick = if (isClickable) onGameModesClick else null,
                    contentDescription = a11yDesc,
                )
            )
        }
        if (game.platforms.isNotEmpty()) {
            val platformsLimit = if (topology.platformsFullWidth) {
                PLATFORMS_PREVIEW_LIMIT_FULL
            } else {
                PLATFORMS_PREVIEW_LIMIT_HALF
            }
            val platformsPreview = formatPlatformsPreview(
                platforms = game.platforms,
                limit = platformsLimit,
            )
            val isClickable = game.platforms.size > 1 || game.releaseDates.size > 1
            val subText = if (platformsPreview.overflowCount > 0) {
                stringResource(R.string.details_more_count, platformsPreview.overflowCount)
            } else {
                null
            }
            val a11yDesc = if (isClickable) {
                stringResource(R.string.details_platforms_more_desc, game.platforms.size)
            } else {
                null
            }
            add(
                FactCardData(
                    testTag = "platforms",
                    icon = Icons.Filled.Devices,
                    title = stringResource(R.string.details_section_platforms),
                    value = platformsPreview.previewText,
                    sub = subText,
                    isClickable = isClickable,
                    onClick = if (isClickable) onPlatformsClick else null,
                    contentDescription = a11yDesc,
                    valueMaxLines = if (topology.platformsFullWidth) 2 else 1,
                )
            )
        }
        if (mainHours != null) {
            add(
                FactCardData(
                    testTag = "time",
                    icon = Icons.Filled.Schedule,
                    title = stringResource(R.string.details_card_time_to_beat),
                    value = stringResource(R.string.details_time_hours_format, mainHours),
                    sub = game.timeToBeatCompleteSeconds?.let { complete ->
                        stringResource(
                            R.string.details_time_complete_format,
                            complete.toDisplayHours()
                        )
                    },
                )
            )
        }
    }

    Column(
        modifier = modifier.padding(horizontal = DETAILS_GUTTER),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cards.chunked(2).forEach { row ->
            if (row.size == 1) {
                // A lone leftover card spans the row so the grid never shows a
                // visually empty half (e.g. [Release][Modes] / [Platforms....]).
                val card = row.first()
                FactCard(
                    icon = card.icon,
                    title = card.title,
                    value = card.value,
                    sub = card.sub,
                    isClickable = card.isClickable,
                    onClick = card.onClick,
                    contentDescription = card.contentDescription,
                    valueMaxLines = card.valueMaxLines,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("details-fact-card-${card.testTag}"),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { card ->
                        FactCard(
                            icon = card.icon,
                            title = card.title,
                            value = card.value,
                            sub = card.sub,
                            isClickable = card.isClickable,
                            onClick = card.onClick,
                            contentDescription = card.contentDescription,
                            valueMaxLines = card.valueMaxLines,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("details-fact-card-${card.testTag}"),
                        )
                    }
                }
            }
        }
    }
}

private data class FactCardData(
    val testTag: String,
    val icon: ImageVector,
    val title: String,
    val value: String,
    val sub: String? = null,
    val isClickable: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val contentDescription: String? = null,
    val valueMaxLines: Int = 1,
)

/** Seconds in half an hour and in an hour, for rounding beats to whole hours. */
private const val HALF_HOUR_SECONDS = 1_800L
private const val HOUR_SECONDS = 3_600L

/** Rounds epoch seconds to whole display hours. */
private fun Long.toDisplayHours(): Long = (this + HALF_HOUR_SECONDS) / HOUR_SECONDS

@Composable
private fun FactCard(
    icon: ImageVector,
    title: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    valueMaxLines: Int = 1,
) {
    val clickModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick, role = Role.Button)
    } else {
        modifier
    }
    val surfaceModifier = if (contentDescription != null) {
        clickModifier.semantics { this.contentDescription = contentDescription }
    } else {
        clickModifier
    }
    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isClickable) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isClickable) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
