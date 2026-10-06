package com.mcifu.mirador.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.model.UsbStorage

/**
 * Carpetas autorizadas a mano. Solo se ven aquí (nunca en la pantalla inicial) y, si así está
 * configurado, después de confirmar el bloqueo del móvil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedFoldersSheet(
    folders: List<UsbStorage>,
    onOpen: (UsbStorage) -> Unit,
    onForget: (UsbStorage) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    var forgetting by remember { mutableStateOf<UsbStorage?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Carpetas guardadas",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "Solo se muestran aquí. Al salir de Mirador se vuelven a bloquear.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (folders.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.FolderOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text(
                        "No hay carpetas guardadas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.label, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                            supportingContent = { Text(if (folder.canBrowse) "Disponible" else "No disponible ahora") },
                            leadingContent = {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { forgetting = folder }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Olvidar ${folder.label}")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clickableItem { onOpen(folder) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir carpeta")
            }
        }
    }
    forgetting?.let { folder ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("¿Olvidar esta carpeta?") },
            text = { Text("Mirador dejará de tener acceso a «${folder.label}». Los archivos no se borran.") },
            confirmButton = {
                TextButton(onClick = { onForget(folder); forgetting = null }) { Text("Olvidar") }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancelar") } },
        )
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    clickable(onClick = onClick)
