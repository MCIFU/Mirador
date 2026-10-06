package com.mcifu.usbx.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mcifu.usbx.domain.model.FileItem

/** Petición de nombre: crear carpeta o renombrar un elemento. */
sealed interface NamingRequest {
    data object CreateFolder : NamingRequest
    data class Rename(val item: FileItem) : NamingRequest
}

/**
 * Barra de selección múltiple: compartir, copiar, mover, eliminar y, con un solo elemento,
 * renombrar, información, abrir con y multiview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    canWrite: Boolean,
    single: FileItem?,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onSingleAction: (FileItem, FileAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(if (count == 1) "1 seleccionado" else "$count seleccionados") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Quitar selección") }
        },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Compartir") }
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar a…") }
            IconButton(onClick = onMove, enabled = canWrite) { Icon(Icons.Outlined.DriveFileMove, contentDescription = "Mover a…") }
            IconButton(onClick = onDelete, enabled = canWrite) { Icon(Icons.Outlined.Delete, contentDescription = "Eliminar") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Más acciones") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Seleccionar todo") },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                        onClick = { menuOpen = false; onSelectAll() },
                    )
                    if (single != null) {
                        if (canWrite) {
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menuOpen = false; onSingleAction(single, FileAction.RENAME) })
                        }
                        DropdownMenuItem(text = { Text("Información") }, onClick = { menuOpen = false; onSingleAction(single, FileAction.INFO) })
                        if (!single.isDirectory) {
                            DropdownMenuItem(text = { Text("Abrir con…") }, onClick = { menuOpen = false; onSingleAction(single, FileAction.OPEN_WITH) })
                        }
                        if (single.type.isVisualMedia) {
                            DropdownMenuItem(text = { Text("Comparar en multiview") }, onClick = { menuOpen = false; onSingleAction(single, FileAction.MULTIVIEW) })
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}
