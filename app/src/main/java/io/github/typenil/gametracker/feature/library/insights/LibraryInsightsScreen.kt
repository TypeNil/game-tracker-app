package io.github.typenil.gametracker.feature.library.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.res.pluralStringResource
import java.text.NumberFormat

internal const val LIBRARY_INSIGHTS_SCREEN_TEST_TAG = "library-insights-screen"
internal const val LIBRARY_INSIGHTS_ACTION_TEST_TAG = "library-insights-action"

private val StatusBarMinWidth = 2.dp
private val StatusBarHeight = 10.dp
private val TasteSeparator = " · "

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

@Composable
private fun InsightsLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = GtDimens.Gutter,
            end = GtDimens.Gutter,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "summary") {
            InsightsSummary(
                insights = insights,
                integerFormat = integerFormat,
                percentFormat = percentFormat,
                ratingFormat = ratingFormat,
            )
        }
        item(key = "chips") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InsightChip(
                    label = stringResource(R.string.library_status_playing),
                    count = integerFormat.format(insights.playingCount),
                )
                InsightChip(
                    label = stringResource(R.string.library_status_wishlist),
                    count = integerFormat.format(insights.wishlistCount),
                )
                InsightChip(
                    label = stringResource(R.string.library_favorite),
                    count = integerFormat.format(insights.favoritesCount),
                )
            }
        }
        item(key = "status") {
            InsightsStatusSection(
                insights = insights,
                integerFormat = integerFormat,
            )
        }
        if (insights.mostPlayed.isNotEmpty()) {
            item(key = "most-played-header") {
                SectionTitle(text = stringResource(R.string.insights_most_played))
            }
            items(
                items = insights.mostPlayed,
                key = { it.gameId },
            ) { game ->
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
        if (insights.topGenres.isNotEmpty()) {
            item(key = "taste") {
                InsightsTasteBlock(
                    title = stringResource(R.string.insights_taste),
                    value = insights.topGenres.joinToString(TasteSeparator),
                )
            }
        }
        if (insights.topPlatforms.isNotEmpty()) {
            item(key = "platforms") {
                InsightsTasteBlock(
                    title = stringResource(R.string.insights_platforms),
                    value = platformLabels(insights.topPlatforms),
                )
            }
        }
    }
}

@Composable
private fun InsightsSummary(
    insights: LibraryInsights,
    integerFormat: NumberFormat,
    percentFormat: NumberFormat,
    ratingFormat: NumberFormat,
) {
    val none = stringResource(R.string.insights_value_none)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.insights_heading),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryCell(
                value = pluralStringResource(
                    R.plurals.insights_games_count,
                    insights.totalGames,
                    integerFormat.format(insights.totalGames),
                ),
                modifier = Modifier.weight(1f),
            )
            SummaryCell(
                value = stringResource(
                    R.string.insights_hours_total,
                    integerFormat.format(insights.totalHours),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryCell(
                value = stringResource(
                    R.string.insights_completed_count,
                    integerFormat.format(insights.completedCount),
                ),
                modifier = Modifier.weight(1f),
            )
            SummaryCell(
                value = insights.averageUserRating?.let { rating ->
                    stringResource(R.string.insights_avg_rating, ratingFormat.format(rating))
                } ?: stringResource(R.string.insights_avg_rating, none),
                modifier = Modifier.weight(1f),
            )
        }
        insights.completionRate?.let { rate ->
            Text(
                text = stringResource(R.string.insights_completion, percentFormat.format(rate)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCell(
    value: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
private fun InsightChip(
    label: String,
    count: String,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.insights_chip, label, count),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(text = stringResource(R.string.insights_status))
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(status.displayNameRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(mostPlayedRowTestTag(game.gameId))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = game.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Text(
            text = hoursLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsTasteBlock(
    title: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(text = title)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
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

@Composable
private fun platformLabels(platforms: List<PlatformFamily>): String {
    val labels = ArrayList<String>(platforms.size)
    for (family in platforms) {
        labels.add(stringResource(family.insightsLabelRes()))
    }
    return labels.joinToString(TasteSeparator)
}
