package com.mcifu.usbx.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.ui.common.FastScroller
import com.mcifu.usbx.ui.common.Formatters
import com.mcifu.usbx.ui.common.label
import com.mcifu.usbx.ui.common.mediaSharedBounds
import kotlinx.coroutines.launch

@Composable
fun FileList(
    items: List<FileItem>,
    state: LazyListState,
    contentPadding: PaddingValues,
    onClick: (FileItem) -> Unit,
    onMore: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.documentId }, contentType = { it.isDirectory }) { item ->
                FileRow(item, onClick = { onClick(item) }, onMore = { onMore(item) })
            }
        }
        FastScroller(
            enabled = items.size > 60,
            isScrolling = state.isScrollInProgress,
            progress = {
                val info = state.layoutInfo
                val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
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
private fun FileRow(item: FileItem, onClick: () -> Unit, onMore: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        supportingContent = {
            val date = Formatters.shortDate(item.lastModified)
            Text(
                if (item.isDirectory) listOf(item.type.label(), date).filter { it.isNotEmpty() }.joinToString(" · ")
                else listOf(Formatters.size(context, item.size), date).filter { it.isNotEmpty() }.joinToString(" · "),
                maxLines = 1,
            )
        },
        leadingContent = {
            FileThumbnail(
                item = item,
                iconSize = 26.dp,
                showVideoBadge = false,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .mediaSharedBounds(item.documentId, enabled = item.type.isVisualMedia),
            )
        },
        trailingContent = {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones de ${item.name}")
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onMore),
    )
}
