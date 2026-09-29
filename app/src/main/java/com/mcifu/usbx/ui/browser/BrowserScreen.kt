package com.mcifu.usbx.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.ViewMode
import com.mcifu.usbx.ui.navigation.BrowserRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateUp: () -> Unit,
    onNavigateToDepth: (Int) -> Unit,
    onOpenFolder: (BrowserRoute) -> Unit,
    viewModel: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    fun open(item: FileItem) {
        if (item.isDirectory) onOpenFolder(viewModel.childRoute(item))
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!state.isLoading && state.error == null) {
                                Text(
                                    countLabel(state.folderCount, state.fileCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        val isGrid = state.settings.viewMode == ViewMode.GRID
                        IconButton(onClick = {
                            viewModel.updateSettings { it.copy(viewMode = if (isGrid) ViewMode.LIST else ViewMode.GRID) }
                        }) {
                            Icon(
                                if (isGrid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = if (isGrid) "Ver como lista" else "Ver como cuadrícula",
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Visualización y orden")
                        }
                    },
                )
                if (state.path.size > 1) {
                    Breadcrumbs(state.path, onNavigateToDepth)
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            val contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())
            when {
                state.isLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::refresh)
                state.items.isEmpty() -> EmptyFolder()
                state.settings.viewMode == ViewMode.GRID -> FileGrid(
                    items = state.items,
                    settings = state.settings,
                    state = gridState,
                    contentPadding = contentPadding,
                    onClick = ::open,
                    onLongClick = {},
                )
                else -> FileList(
                    items = state.items,
                    state = listState,
                    contentPadding = contentPadding,
                    onClick = ::open,
                    onMore = {},
                )
            }
        }
    }

    if (showSettings) {
        DisplaySettingsSheet(
            settings = state.settings,
            onChange = viewModel::updateSettings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun Breadcrumbs(path: List<String>, onNavigateToDepth: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(path.size) { listState.scrollToItem(path.lastIndex) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(path) { index, name ->
            val isLast = index == path.lastIndex
            TextButton(onClick = { onNavigateToDepth(index) }, enabled = !isLast) {
                Text(
                    name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
            if (!isLast) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun countLabel(folders: Int, files: Int): String {
    val parts = buildList {
        if (folders > 0) add(if (folders == 1) "1 carpeta" else "$folders carpetas")
        if (files > 0) add(if (files == 1) "1 archivo" else "$files archivos")
    }
    return parts.joinToString(" · ").ifEmpty { "Vacía" }
}
