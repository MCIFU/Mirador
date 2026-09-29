package com.mcifu.usbx.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcifu.usbx.domain.model.BrowserSettings
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.GridLayout
import com.mcifu.usbx.ui.common.FastScroller
import com.mcifu.usbx.ui.common.label
import com.mcifu.usbx.ui.common.mediaSharedBounds
import kotlinx.coroutines.launch

@Composable
fun FileGrid(
    items: List<FileItem>,
    settings: BrowserSettings,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onClick: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalWindowInfo.current.containerSize
    val screenAspect = if (window.height > 0) window.width.toFloat() / window.height else 0.5f
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = GridLayout.columnCount(maxWidth.value - 8f, screenAspect, settings)
        val iconSize = (maxWidth / columns * 0.38f).coerceIn(24.dp, 72.dp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = PaddingValues(
                start = 4.dp, end = 4.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.documentId }, contentType = { it.type }) { item ->
                GridTile(
                    item = item,
                    iconSize = iconSize,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                )
            }
        }

        FastScroller(
            enabled = items.size > columns * 24,
            isScrolling = state.isScrollInProgress,
            progress = {
                val info = state.layoutInfo
                val visible = info.visibleItemsInfo.size
                val scrollable = (info.totalItemsCount - visible).coerceAtLeast(1)
                state.firstVisibleItemIndex.toFloat() / scrollable
            },
            onScrollTo = { fraction ->
                val info = state.layoutInfo
                val target = (fraction * (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)).toInt()
                scope.launch { state.scrollToItem(target) }
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(
    item: FileItem,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "${item.type.label()}: ${item.name}" },
    ) {
        if (item.type.isVisualMedia) {
            // Fotos y vídeos: solo la imagen, como en una galería. La foto "crece" hacia el visor.
            FileThumbnail(
                item,
                iconSize = iconSize,
                modifier = Modifier.fillMaxSize().mediaSharedBounds(item.documentId),
            )
        } else {
            // Carpetas y otros archivos: icono (o carátula) con el nombre debajo.
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FileThumbnail(
                    item,
                    iconSize = iconSize,
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(shape),
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
    }
}
