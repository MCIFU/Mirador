package com.mcifu.usbx.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.StorageException
import com.mcifu.usbx.ui.common.Formatters
import com.mcifu.usbx.ui.common.icon
import com.mcifu.usbx.ui.common.tint
import com.mcifu.usbx.ui.navigation.BrowserRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateUp: () -> Unit,
    onOpenFolder: (BrowserRoute) -> Unit,
    viewModel: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.path.size > 1) {
                            Text(
                                state.path.joinToString(" › "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.StartEllipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::refresh)
                state.items.isEmpty() -> EmptyFolder()
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.documentId }) { item ->
                        FileRow(item, onClick = { if (item.isDirectory) onOpenFolder(viewModel.childRoute(item)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(item: FileItem, onClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        supportingContent = {
            Text(
                if (item.isDirectory) Formatters.shortDate(item.lastModified)
                else "${Formatters.size(context, item.size)} · ${Formatters.shortDate(item.lastModified)}",
            )
        },
        leadingContent = {
            Icon(item.type.icon(), contentDescription = null, tint = item.type.tint(MaterialTheme.colorScheme))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
internal fun EmptyFolder() {
    CenteredMessage(icon = { Icon(Icons.Outlined.FolderOff, null, Modifier.size(48.dp)) }, title = "Carpeta vacía")
}

@Composable
internal fun ErrorState(reason: StorageException.Reason, onRetry: () -> Unit) {
    val (title, body) = when (reason) {
        StorageException.Reason.DISCONNECTED ->
            "La memoria USB se ha desconectado" to "Vuelve a conectarla para seguir."
        StorageException.Reason.PERMISSION_DENIED ->
            "Sin permiso de acceso" to "Android ha retirado el acceso a esta memoria. Vuelve al inicio y concédelo de nuevo."
        StorageException.Reason.NOT_FOUND ->
            "La carpeta ya no existe" to "Puede que se haya movido o borrado desde otro dispositivo."
        StorageException.Reason.READ_ERROR ->
            "Error de lectura" to "No se ha podido leer la memoria. Comprueba la conexión e inténtalo de nuevo."
    }
    CenteredMessage(
        icon = { Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
        title = title,
        body = body,
        action = { Button(onClick = onRetry) { Text("Reintentar") } },
    )
}

@Composable
private fun CenteredMessage(
    icon: @Composable () -> Unit,
    title: String,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}
