package io.github.typenil.gametracker.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.typenil.gametracker.feature.library.LibraryTab

@Composable
internal fun LibraryTabRow(
    selectedTabIndex: Int,
    tabCounts: Map<LibraryTab, Int>,
    onTabClick: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("library_tab_row")
    ) {
        LibraryTab.entries.forEach { tab ->
            val isSelected = selectedTabIndex == tab.ordinal
            val count = tabCounts[tab] ?: 0
            Tab(
                selected = isSelected,
                onClick = { onTabClick(tab) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(tab.titleRes),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Badge(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ) {
                            Text(text = "$count")
                        }
                    }
                }
            )
        }
    }
}
