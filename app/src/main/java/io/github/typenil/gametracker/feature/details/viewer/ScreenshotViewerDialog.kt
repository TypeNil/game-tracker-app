package io.github.typenil.gametracker.feature.details.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.typenil.gametracker.R

@Composable
internal fun ScreenshotViewerDialog(
    screenshots: List<String>,
    initialIndex: Int,
    onDismissRequest: () -> Unit,
    onPageChanged: (Int) -> Unit,
    imageReloadToken: Long = 0L,
) {
    if (screenshots.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, screenshots.lastIndex),
        pageCount = { screenshots.size },
    )
    var activeScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            activeScale = 1f
            onPageChanged(it)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = activeScale <= MIN_ZOOM + ZOOM_EPSILON,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableScreenshotImage(
                    model = screenshots[page],
                    contentDescription = stringResource(
                        R.string.details_viewer_page_format,
                        page + 1,
                        screenshots.size,
                    ),
                    isCurrentPage = pagerState.currentPage == page,
                    onScaleChanged = { newScale ->
                        if (pagerState.currentPage == page) {
                            activeScale = newScale
                        }
                    },
                    imageReloadToken = imageReloadToken,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.details_viewer_page_format,
                        pagerState.currentPage + 1,
                        screenshots.size
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.details_viewer_close_desc),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
