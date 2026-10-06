package com.mcifu.mirador.ui.browser

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.OperationKind
import com.mcifu.mirador.domain.model.ViewMode
import com.mcifu.mirador.ui.common.CannotOpenDialog
import com.mcifu.mirador.ui.common.ExternalActions
import com.mcifu.mirador.ui.common.FileInfoDialog
import com.mcifu.mirador.ui.navigation.BrowserRoute
import com.mcifu.mirador.ui.navigation.MultiviewRoute
import com.mcifu.mirador.ui.navigation.ViewerRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateUp: () -> Unit,
    onNavigateToDepth: (Int) -> Unit,
    onOpenFolder: (BrowserRoute) -> Unit,
    onOpenViewer: (ViewerRoute) -> Unit,
    onOpenMultiview: (MultiviewRoute) -> Unit,
    onGoHome: () -> Unit,
    returnedFromDocumentId: String?,
    onReturnHandled: () -> Unit,
    viewModel: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val canWrite by viewModel.canWrite.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val writableStorages by viewModel.writableStorages.collectAsStateWithLifecycle()
    val selectionMode = selection.isNotEmpty()

    var naming by remember { mutableStateOf<NamingRequest?>(null) }
    var deleting by remember { mutableStateOf<List<FileItem>?>(null) }
    var picking by remember { mutableStateOf<Pair<OperationKind, List<FileItem>>?>(null) }
    var actionsFor by remember { mutableStateOf<FileItem?>(null) }
    var infoFor by remember { mutableStateOf<FileItem?>(null) }
    var cannotOpen by remember { mutableStateOf<Pair<FileItem, Boolean>?>(null) }

    fun openExternally(item: FileItem) {
        when (val result = ExternalActions.open(context, item)) {
            ExternalActions.OpenResult.Opened -> Unit
            is ExternalActions.OpenResult.NoApp -> cannotOpen = item to result.canOpenGeneric
        }
    }

    fun open(item: FileItem) {
        when {
            selectionMode -> viewModel.toggleSelection(item)
            item.isDirectory -> onOpenFolder(viewModel.childRoute(item))
            viewModel.canViewInternally(item) -> {
                Diagnostics.log("ABRIR", "${item.name} (${item.type}, ${item.mimeType}) → visor de Mirador")
                try {
                    onOpenViewer(viewModel.viewerRoute(item))
                } catch (e: Exception) {
                    Diagnostics.log("ABRIR", "No se pudo abrir el visor", e)
                    scope.launch { snackbar.showSnackbar("No se pudo abrir el visor: ${e.javaClass.simpleName}: ${e.message}") }
                }
            }
            else -> {
                Diagnostics.log("ABRIR", "${item.name} (${item.type}, ${item.mimeType}) → otra app")
                openExternally(item)
            }
        }
    }

    fun onAction(item: FileItem, action: FileAction) {
        actionsFor = null
        when (action) {
            FileAction.OPEN -> open(item)
            FileAction.OPEN_WITH -> if (!ExternalActions.openWith(context, item)) {
                scope.launch { snackbar.showSnackbar("No hay aplicaciones para abrir este archivo.") }
            }
            FileAction.MULTIVIEW -> onOpenMultiview(viewModel.multiviewRoute(item))
            FileAction.INFO -> infoFor = item
            FileAction.SHARE -> if (!ExternalActions.share(context, listOf(item))) {
                scope.launch { snackbar.showSnackbar("No hay aplicaciones con las que compartir.") }
            }
            FileAction.RENAME -> naming = NamingRequest.Rename(item)
            FileAction.COPY -> picking = OperationKind.COPY to listOf(item)
            FileAction.MOVE -> picking = OperationKind.MOVE to listOf(item)
            FileAction.DELETE -> deleting = listOf(item)
        }
    }

    // Atrás con elementos seleccionados: primero se quita la selección.
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    // Al volver del visor, deja visible la última imagen vista (como una galería).
    LaunchedEffect(returnedFromDocumentId, state.items) {
        val documentId = returnedFromDocumentId ?: return@LaunchedEffect
        val index = state.items.indexOfFirst { it.documentId == documentId }
        if (index >= 0) {
            val gridVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
            val listVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (state.settings.viewMode == ViewMode.GRID && !gridVisible) gridState.scrollToItem(index)
            if (state.settings.viewMode == ViewMode.LIST && !listVisible) listState.scrollToItem(index)
        }
        if (state.items.isNotEmpty()) onReturnHandled()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selection.size,
                    canWrite = canWrite,
                    single = viewModel.selectedItems().singleOrNull(),
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onShare = {
                        if (!ExternalActions.share(context, viewModel.selectedItems())) {
                            scope.launch { snackbar.showSnackbar("Selecciona al menos un archivo (las carpetas no se pueden compartir).") }
                        }
                    },
                    onCopy = { picking = OperationKind.COPY to viewModel.selectedItems() },
                    onMove = { picking = OperationKind.MOVE to viewModel.selectedItems() },
                    onDelete = { deleting = viewModel.selectedItems() },
                    onSingleAction = { item, action -> onAction(item, action) },
                )
            } else Column {
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
                        IconButton(onClick = { naming = NamingRequest.CreateFolder }, enabled = canWrite && state.error == null) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Nueva carpeta")
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
        snackbarHost = { SnackbarHost(snackbar) },
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
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::refresh, onGoHome = onGoHome)
                state.items.isEmpty() -> EmptyFolder()
                state.settings.viewMode == ViewMode.GRID -> FileGrid(
                    items = state.items,
                    settings = state.settings,
                    state = gridState,
                    contentPadding = contentPadding,
                    onClick = ::open,
                    onLongClick = viewModel::toggleSelection,
                    selectedIds = selection,
                )
                else -> FileList(
                    items = state.items,
                    state = listState,
                    contentPadding = contentPadding,
                    onClick = ::open,
                    onMore = { actionsFor = it },
                    onLongClick = viewModel::toggleSelection,
                    selectedIds = selection,
                )
            }
            operation?.let { progress ->
                OperationProgressCard(
                    progress = progress,
                    onCancel = viewModel::cancelOperation,
                    onDismiss = viewModel::dismissOperation,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding()),
                )
            }
        }
    }

    actionsFor?.let { item ->
        FileActionsSheet(item = item, canWrite = canWrite, onAction = { onAction(item, it) }, onDismiss = { actionsFor = null })
    }
    infoFor?.let { item ->
        FileInfoDialog(item = item, loadDetails = viewModel::loadDetails, onDismiss = { infoFor = null })
    }
    cannotOpen?.let { (item, canOpenGeneric) ->
        CannotOpenDialog(
            fileName = item.name,
            canOpenGeneric = canOpenGeneric,
            onOpenWithOther = {
                cannotOpen = null
                ExternalActions.openWith(context, item, anyType = true)
            },
            onDismiss = { cannotOpen = null },
        )
    }

    naming?.let { request ->
        val existing = state.items.map { it.name }
        when (request) {
            NamingRequest.CreateFolder -> NameDialog(
                title = "Nueva carpeta",
                confirmLabel = "Crear",
                initialName = "",
                isDirectory = true,
                existingNames = existing,
                onConfirm = viewModel::createFolder,
                onDismiss = { naming = null },
            )
            is NamingRequest.Rename -> NameDialog(
                title = "Renombrar",
                confirmLabel = "Renombrar",
                initialName = request.item.name,
                isDirectory = request.item.isDirectory,
                existingNames = existing,
                onConfirm = { viewModel.rename(request.item, it) },
                onDismiss = { naming = null },
            )
        }
    }
    deleting?.let { items ->
        DeleteConfirmDialog(
            items = items,
            onConfirm = {
                deleting = null
                if (!viewModel.delete(items)) scope.launch { snackbar.showSnackbar("Espera a que termine la operación en curso.") }
            },
            onDismiss = { deleting = null },
        )
    }
    picking?.let { (kind, items) ->
        val isMove = kind == OperationKind.MOVE
        FolderPickerSheet(
            title = (if (isMove) "Mover " else "Copiar ") + if (items.size == 1) "«${items.first().name}»" else "${items.size} elementos",
            confirmLabel = if (isMove) "Mover" else "Copiar",
            initialPath = remember(items) { viewModel.pickerStart() },
            storages = writableStorages,
            loadFolders = viewModel::foldersIn,
            onPick = { target ->
                picking = null
                val started = if (isMove) viewModel.moveTo(items, target) else viewModel.copyTo(items, target)
                if (!started) scope.launch { snackbar.showSnackbar("Espera a que termine la operación en curso.") }
            },
            onDismiss = { picking = null },
        )
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
