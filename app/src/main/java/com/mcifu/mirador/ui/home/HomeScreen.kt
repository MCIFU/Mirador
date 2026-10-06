package com.mcifu.mirador.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.domain.model.StorageKind
import com.mcifu.mirador.domain.model.UsbStorage
import com.mcifu.mirador.ui.common.CrashNoticeDialog
import com.mcifu.mirador.ui.common.DiagnosticsDialog
import com.mcifu.mirador.ui.common.rememberDeviceAuthenticator
import kotlinx.coroutines.launch

/**
 * Pantalla inicial. Solo muestra memorias USB y tarjetas SD; las carpetas autorizadas a mano
 * no aparecen aquí: están en «Carpetas guardadas», tras el bloqueo del móvil si así se elige.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenStorage: (UsbStorage) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val storages by viewModel.storages.collectAsStateWithLifecycle()
    val foldersUnlocked by viewModel.foldersUnlocked.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var crashNotice by rememberSaveable { mutableStateOf(Diagnostics.lastCrash() != null) }
    val scope = rememberCoroutineScope()

    // Al salir de la app las carpetas se vuelven a bloquear y la hoja se cierra: en la vista de
    // apps recientes no queda la lista a la vista.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.lockFolders()
        showFolders = false
    }

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

    val authenticate = rememberDeviceAuthenticator(
        title = "Carpetas guardadas",
        subtitle = "Confirma que eres tú para verlas",
    ) { ok ->
        if (ok) {
            viewModel.unlockFolders()
            showFolders = true
        }
    }

    fun openSavedFolders() {
        if (!appearance.lockSavedFolders || foldersUnlocked) showFolders = true else authenticate()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenStorage -> {
                    showFolders = false
                    onOpenStorage(event.storage)
                }
                HomeEvent.AccessDenied -> {
                    val result = snackbar.showSnackbar(
                        message = "Permiso rechazado. Sin acceso, Mirador no puede leer la memoria.",
                        actionLabel = "Reintentar",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        requestAccess(viewModel.storages.value.firstOrNull { !it.isAuthorized && it.isAvailable })
                    }
                }
                HomeEvent.InvalidSelection -> snackbar.showSnackbar("La ubicación elegida no es válida.")
                is HomeEvent.StorageNotReady -> snackbar.showSnackbar(
                    when {
                        event.storage.kind == StorageKind.FOLDER -> "La carpeta no está disponible ahora."
                        !event.storage.isAvailable -> "La memoria USB no está conectada."
                        else -> "Primero concede acceso a la memoria USB."
                    },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mirador",
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
                                text = { Text("Carpetas guardadas") },
                                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                onClick = { menuOpen = false; openSavedFolders() },
                            )
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
        if (showFolders) {
            SavedFoldersSheet(
                folders = storages.filter { it.kind == StorageKind.FOLDER && it.isAuthorized },
                onOpen = viewModel::open,
                onForget = viewModel::forget,
                onAdd = { requestAccess(null) },
                onDismiss = { showFolders = false },
            )
        }

        // Memorias físicas: USB y tarjetas SD. Las carpetas manuales nunca se listan aquí.
        val devices = storages.filter { it.kind != StorageKind.FOLDER }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (devices.isEmpty()) {
                item(key = "waiting") { WaitingForStorage(Modifier.animateItem().padding(top = 24.dp, bottom = 8.dp)) }
            } else {
                items(devices, key = { it.id }) { storage ->
                    if (storage.canBrowse) {
                        ReadyStorageCard(storage, onOpen = { viewModel.open(storage) }, modifier = Modifier.animateItem())
                    } else {
                        StorageCard(
                            storage = storage,
                            onRequestAccess = { requestAccess(storage) },
                            onForget = { viewModel.forget(storage) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            item(key = "actions") {
                QuickActions(
                    onPickFolder = { requestAccess(null) },
                    onSavedFolders = ::openSavedFolders,
                    locked = appearance.lockSavedFolders && !foldersUnlocked,
                    modifier = Modifier.animateItem().padding(top = 4.dp),
                )
            }
        }
    }
}
