package io.github.typenil.gametracker.feature.library.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.typenil.gametracker.R
import io.github.typenil.gametracker.core.designsystem.component.PlatformFamily
import io.github.typenil.gametracker.core.designsystem.component.contentColor
import io.github.typenil.gametracker.core.designsystem.component.displayNameRes
import io.github.typenil.gametracker.core.designsystem.component.errorMessage
import io.github.typenil.gametracker.core.designsystem.theme.GtDimens
import io.github.typenil.gametracker.core.model.AppError
import io.github.typenil.gametracker.core.model.LibraryStatus
import java.text.NumberFormat

internal const val LIBRARY_INSIGHTS_SCREEN_TEST_TAG = "library-insights-screen"
internal const val LIBRARY_INSIGHTS_ACTION_TEST_TAG = "library-insights-action"
internal const val LIBRARY_INSIGHTS_LIST_TEST_TAG = "library-insights-list"
internal const val LIBRARY_INSIGHTS_LOADING_TEST_TAG = "library-insights-loading"

private val StatusBarMinWidth = 2.dp
private val StatusBarHeight = 14.dp
private val SectionCardShape = RoundedCornerShape(16.dp)
private val InsightsFilterChipShape = RoundedCornerShape(8.dp)
private val ChipShape = RoundedCornerShape(50)

private const val INSIGHTS_SKELETON_TITLE_FRACTION = 0.45f
private const val INSIGHTS_SKELETON_STATUS_TITLE_FRACTION = 0.28f
private const val INSIGHTS_SKELETON_PLAYED_TITLE_FRACTION = 0.4f
private const val INSIGHTS_SKELETON_METRIC_VALUE_FRACTION = 0.5f
private const val INSIGHTS_SKELETON_METRIC_CAPTION_FRACTION = 0.7f

@Composable
fun LibraryInsightsRoute(
    onBackClick: () -> Unit,
    onGameClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryInsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryInsightsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onGameClick = onGameClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryInsightsScreen(
    uiState: LibraryInsightsUiState,
    onBackClick: () -> Unit,
    onGameClick: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars),
        modifier = modifier
            .fillMaxSize()
            .testTag(LIBRARY_INSIGHTS_SCREEN_TEST_TAG),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.insights_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val bodyModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (uiState) {
            LibraryInsightsUiState.Loading -> InsightsLoading(modifier = bodyModifier)
            is LibraryInsightsUiState.Error -> InsightsErrorState(
                error = uiState.error,
                onRetry = onRetry,
                modifier = bodyModifier,
            )
            LibraryInsightsUiState.Empty -> InsightsEmptyState(modifier = bodyModifier)
            is LibraryInsightsUiState.Content -> InsightsContent(
                insights = uiState.insights,
                onGameClick = onGameClick,
                modifier = bodyModifier,
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightsLoading(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.insights_loading)
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(LIBRARY_INSIGHTS_LOADING_TEST_TAG)
            .semantics { contentDescription = loadingDescription }
            .padding(
                start = GtDimens.Gutter,
                end = GtDimens.Gutter,
                top = 8.dp,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InsightsSectionCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth(INSIGHTS_SKELETON_TITLE_FRACTION)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                InsightsMetricPlaceholder(barColor = barColor, modifier = Modifier.weight(1f))
                InsightsMetricPlaceholder(barColor = barColor, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                InsightsMetricPlaceholder(barColor = barColor, modifier = Modifier.weight(1f))
                InsightsMetricPlaceholder(barColor = barColor, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .height(32.dp)
                            .clip(ChipShape)
                            .background(barColor),
                    )
                }
            }
        }
        InsightsSectionCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth(INSIGHTS_SKELETON_STATUS_TITLE_FRACTION)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
            Spacer(modifier = Modifier.height(12.dp))
            repeat(4) { index ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor),
                )
            }
        }
        InsightsSectionCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth(INSIGHTS_SKELETON_PLAYED_TITLE_FRACTION)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
            Spacer(modifier = Modifier.height(8.dp))
            repeat(2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(barColor),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsMetricPlaceholder(
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(INSIGHTS_SKELETON_METRIC_VALUE_FRACTION)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(INSIGHTS_SKELETON_METRIC_CAPTION_FRACTION)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor),
        )
    }
}

@Composable
private fun InsightsErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(GtDimens.Empty), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = error.errorMessage(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.retry_button))
            }
        }
    }
}

@Composable
private fun InsightsEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(GtDimens.Empty), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(R.string.insights_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.insights_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightsContent(
    insights: LibraryInsights,
    onGameClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val integerFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val percentFormat = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }
    }
    val ratingFormat = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(LIBRARY_INSIGHTS_LIST_TEST_TAG),
        contentPadding = PaddingValues(
            start = GtDimens.Gutter,
            end = GtDimens.Gutter,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            InsightsSummaryCard(
                insights = insights,
                integerFormat = integerFormat,
                percentFormat = percentFormat,
                ratingFormat = ratingFormat,
            )
        }
        item(key = "status") {
            InsightsSectionCard {
                InsightsStatusSection(
                    insights = insights,
                    integerFormat = integerFormat,
                )
            }
        }
        if (insights.mostPlayed.isNotEmpty()) {
            item(key = "most-played") {
                InsightsSectionCard {
                    SectionTitle(text = stringResource(R.string.insights_most_played))
                    Spacer(modifier = Modifier.height(4.dp))
                    insights.mostPlayed.forEachIndexed { index, game ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        MostPlayedRow(
                            game = game,
                            hoursLabel = stringResource(
                                R.string.insights_hours_total,
                                integerFormat.format(game.hoursPlayed),
                            ),
                            onClick = { onGameClick(game.gameId) },
                        )
                    }
                }
            }
        }
        if (insights.topGenres.isNotEmpty()) {
            item(key = "taste") {
                InsightsSectionCard {
                    SectionTitle(text = stringResource(R.string.insights_taste))
                    Spacer(modifier = Modifier.height(8.dp))
                    TasteChipRow(labels = insights.topGenres)
                }
            }
        }
        if (insights.topPlatforms.isNotEmpty()) {
            item(key = "platforms") {
                InsightsSectionCard {
                    SectionTitle(text = stringResource(R.string.insights_platforms))
                    Spacer(modifier = Modifier.height(8.dp))
                    PlatformChipRow(platforms = insights.topPlatforms)
                }
            }
        }
    }
}

@Composable
private fun InsightsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SectionCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(GtDimens.Gutter),
            content = { content() },
        )
    }
}

@Composable
private fun InsightsSummaryCard(
    insights: LibraryInsights,
    integerFormat: NumberFormat,
    percentFormat: NumberFormat,
    ratingFormat: NumberFormat,
) {
    val none = stringResource(R.string.insights_value_none)
    val gamesValue = integerFormat.format(insights.totalGames)
    val gamesCaption = pluralStringResource(
        R.plurals.insights_games_caption,
        insights.totalGames,
    )
    val gamesA11y = pluralStringResource(
        R.plurals.insights_games_count,
        insights.totalGames,
        gamesValue,
    )
    val hoursValue = integerFormat.format(insights.totalHours)
    val completedValue = integerFormat.format(insights.completedCount)
    val completionLabel = insights.completionRate?.let { rate ->
        percentFormat.format(rate)
    }
    val completedCaption = if (completionLabel != null) {
        stringResource(R.string.insights_metric_completed_rate, completionLabel)
    } else {
        stringResource(R.string.insights_metric_completed)
    }
    val completedA11y = if (completionLabel != null) {
        stringResource(R.string.insights_completed_count, completedValue) +
            ", " +
            stringResource(R.string.insights_completion, completionLabel)
    } else {
        stringResource(R.string.insights_completed_count, completedValue)
    }
    val ratingValue = insights.averageUserRating?.let { ratingFormat.format(it) } ?: none
    InsightsSectionCard {
        Text(
            text = stringResource(R.string.insights_heading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCell(
                value = gamesValue,
                caption = gamesCaption,
                contentDescription = gamesA11y,
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                value = hoursValue,
                caption = stringResource(R.string.insights_metric_hours),
                contentDescription = stringResource(R.string.insights_hours_total, hoursValue),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCell(
                value = completedValue,
                caption = completedCaption,
                contentDescription = completedA11y,
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                value = ratingValue,
                caption = stringResource(R.string.insights_metric_rating),
                contentDescription = stringResource(R.string.insights_avg_rating, ratingValue),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InsightChip(
                label = stringResource(LibraryStatus.PLAYING.displayNameRes()),
                count = integerFormat.format(insights.playingCount),
                swatch = LibraryStatus.PLAYING.contentColor(),
            )
            InsightChip(
                label = stringResource(LibraryStatus.WISHLIST.displayNameRes()),
                count = integerFormat.format(insights.wishlistCount),
                swatch = LibraryStatus.WISHLIST.contentColor(),
            )
            InsightChip(
                label = stringResource(R.string.library_filter_favorites),
                count = integerFormat.format(insights.favoritesCount),
                swatch = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MetricCell(
    value: String,
    caption: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InsightChip(
    label: String,
    count: String,
    swatch: Color? = null,
) {
    Surface(
        shape = ChipShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (swatch != null) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = swatch,
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InsightsStatusSection(
    insights: LibraryInsights,
    integerFormat: NumberFormat,
) {
    val rows = listOf(
        LibraryStatus.COMPLETED to insights.completedCount,
        LibraryStatus.WISHLIST to insights.wishlistCount,
        LibraryStatus.PLAYING to insights.playingCount,
        LibraryStatus.DROPPED to insights.droppedCount,
    )
    val maxCount = rows.maxOf { it.second }
    SectionTitle(text = stringResource(R.string.insights_status))
    Spacer(modifier = Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { (status, count) ->
            StatusBarRow(
                status = status,
                count = count,
                maxCount = maxCount,
                countLabel = integerFormat.format(count),
            )
        }
    }
}

@Composable
private fun StatusBarRow(
    status: LibraryStatus,
    count: Int,
    maxCount: Int,
    countLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(status.displayNameRes()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(StatusBarHeight)
                .clip(RoundedCornerShape(50))
                .clearAndSetSemantics { },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxSize(),
            ) {}
            val barWidth = when {
                count == 0 || maxCount == 0 -> 0.dp
                else -> maxOf(StatusBarMinWidth, maxWidth * (count.toFloat() / maxCount))
            }
            if (barWidth > 0.dp) {
                Surface(
                    color = status.contentColor(),
                    modifier = Modifier
                        .width(barWidth)
                        .height(StatusBarHeight),
                ) {}
            }
        }
    }
}

@Composable
private fun MostPlayedRow(
    game: MostPlayedGame,
    hoursLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(mostPlayedRowTestTag(game.gameId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = game.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = hoursLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InsightsFilterChip(
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = InsightsFilterChipShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 12.dp)
                .widthIn(max = 200.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leadingIcon?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TasteChipRow(labels: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            InsightsFilterChip(label = label)
        }
    }
}

@Composable
private fun PlatformChipRow(platforms: List<PlatformFamily>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        platforms.forEach { family ->
            InsightsFilterChip(
                label = stringResource(family.insightsLabelRes()),
                leadingIcon = {
                    Icon(
                        painter = painterResource(family.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

internal fun mostPlayedRowTestTag(gameId: Long): String = "library-insights-most-played-$gameId"

internal fun PlatformFamily.insightsLabelRes(): Int = when (this) {
    PlatformFamily.PLAYSTATION -> R.string.platform_playstation
    PlatformFamily.XBOX -> R.string.platform_xbox
    PlatformFamily.NINTENDO -> R.string.insights_platform_nintendo
    PlatformFamily.PC -> R.string.platform_pc
}

