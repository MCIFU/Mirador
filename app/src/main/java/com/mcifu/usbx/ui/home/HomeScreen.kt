package com.mcifu.usbx.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcifu.usbx.domain.model.MountState
import com.mcifu.usbx.domain.model.StorageKind
import com.mcifu.usbx.domain.model.UsbStorage
import com.mcifu.usbx.data.diagnostics.Diagnostics
import com.mcifu.usbx.ui.common.CrashNoticeDialog
import com.mcifu.usbx.ui.common.DiagnosticsDialog
import com.mcifu.usbx.ui.common.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenStorage: (UsbStorage) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val storages by viewModel.storages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var crashNotice by rememberSaveable { mutableStateOf(Diagnostics.lastCrash() != null) }
    val scope = rememberCoroutineScope()

    val accessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        viewModel.onAccessResult(
            treeUri = if (result.resultCode == Activity.RESULT_OK) data?.data else null,
            flags = data?.flags ?: 0,
        )
    }

    fun requestAccess(storage: UsbStorage?) {
        try {
            accessLauncher.launch(viewModel.accessIntent(storage))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Este dispositivo no tiene selector de carpetas del sistema.") }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenStorage -> onOpenStorage(event.storage)
                HomeEvent.AccessDenied -> {
                    val result = snackbar.showSnackbar(
                        message = "Permiso rechazado. Sin acceso, USBX no puede leer la memoria.",
                        actionLabel = "Reintentar",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        requestAccess(viewModel.storages.value.firstOrNull { !it.isAuthorized && it.isAvailable })
                    }
                }
                HomeEvent.InvalidSelection -> snackbar.showSnackbar("La ubicación elegida no es válida.")
                is HomeEvent.StorageNotReady -> snackbar.showSnackbar(
                    if (!event.storage.isAvailable) "La memoria USB no está conectada."
                    else "Primero concede acceso a la memoria USB.",
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "USBX",
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Buscar de nuevo")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Ajustes") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                            DropdownMenuItem(
                                text = { Text("Informe de diagnóstico") },
                                leadingIcon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                                onClick = { menuOpen = false; showDiagnostics = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (crashNotice) {
            CrashNoticeDialog(
                onShowReport = { crashNotice = false; showDiagnostics = true },
                onDismiss = { crashNotice = false; Diagnostics.clearCrash() },
            )
        }
        if (showDiagnostics) {
            DiagnosticsDialog(storages = storages, onDismiss = { showDiagnostics = false })
        }
        val available = storages.filter { it.kind != StorageKind.FOLDER || it.isAuthorized }
        if (available.none { it.kind != StorageKind.FOLDER }) {
            EmptyState(
                folders = available.filter { it.kind == StorageKind.FOLDER },
                padding = padding,
                onPickFolder = { requestAccess(null) },
                onOpen = viewModel::open,
                onForget = viewModel::forget,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(available, key = { it.id }) { storage ->
                    StorageCard(
                        storage = storage,
                        onOpen = { viewModel.open(storage) },
                        onRequestAccess = { requestAccess(storage) },
                        onForget = { viewModel.forget(storage) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item {
                    TextButton(onClick = { requestAccess(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Elegir carpeta manualmente")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    folders: List<UsbStorage>,
    padding: PaddingValues,
    onPickFolder: () -> Unit,
    onOpen: (UsbStorage) -> Unit,
    onForget: (UsbStorage) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp,
            top = padding.calculateTopPadding() + 48.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(112.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Usb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        item {
            Text(
                "Conecta una memoria USB",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item {
            Text(
                "Usa un pendrive USB‑C o un adaptador OTG. USBX lo detectará en cuanto Android lo monte.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        item {
            OutlinedButton(onClick = onPickFolder, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Elegir carpeta manualmente")
            }
        }
        items(folders, key = { it.id }) { folder ->
            StorageCard(
                storage = folder,
                onOpen = { onOpen(folder) },
                onRequestAccess = onPickFolder,
                onForget = { onForget(folder) },
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }
}

@Composable
private fun StorageCard(
    storage: UsbStorage,
    onOpen: () -> Unit,
    onRequestAccess: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = storage.canBrowse
    Card(
        onClick = onOpen,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainer,
            disabledContainerColor = colors.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (storage.isAvailable) colors.primaryContainer else colors.surfaceVariant,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                !storage.isAvailable -> Icons.Outlined.UsbOff
                                storage.kind == StorageKind.SD_CARD -> Icons.Outlined.SdCard
                                storage.kind == StorageKind.FOLDER -> Icons.Outlined.Folder
                                else -> Icons.Outlined.Usb
                            },
                            contentDescription = null,
                            tint = if (storage.isAvailable) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        storage.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText(storage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            val total = storage.totalBytes
            val free = storage.freeBytes
            if (storage.isAvailable && total != null && free != null && total > 0) {
                val context = LocalContext.current
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Formatters.size(context, free)} libres de ${Formatters.size(context, total)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (storage.mountState == MountState.CHECKING) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    storage.isAvailable && !storage.isAuthorized ->
                        Button(onClick = onRequestAccess) { Text("Conceder acceso") }
                    storage.canBrowse ->
                        FilledTonalButton(onClick = onOpen) { Text("Explorar") }
                    storage.isAuthorized && !storage.isAvailable ->
                        TextButton(onClick = onForget, modifier = Modifier.alpha(0.9f)) { Text("Olvidar") }
                }
            }
        }
    }
}

private fun statusText(storage: UsbStorage): String = when (storage.mountState) {
    MountState.CHECKING -> "Comprobando la memoria…"
    MountState.UNAVAILABLE -> if (storage.isAuthorized) "Desconectada · acceso guardado" else "No disponible"
    MountState.READ_ONLY -> if (storage.isAuthorized) "Conectada · solo lectura" else "Conectada · sin acceso"
    MountState.MOUNTED -> when {
        storage.kind == StorageKind.FOLDER -> "Carpeta autorizada"
        storage.isAuthorized -> "Conectada · acceso concedido"
        else -> "Conectada · falta conceder acceso"
    }
}
