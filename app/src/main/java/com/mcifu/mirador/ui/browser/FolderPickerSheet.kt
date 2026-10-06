package com.mcifu.mirador.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.FolderLocation
import com.mcifu.mirador.domain.model.StorageKind
import com.mcifu.mirador.domain.model.UsbStorage

/** Un nivel del selector: ubicación y nombre visible. */
data class PickerLevel(val location: FolderLocation, val name: String)

/**
 * Selector de carpeta de destino para copiar o mover. Empieza en la carpeta actual; se puede subir,
 * entrar en subcarpetas o cambiar a otra memoria autorizada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    title: String,
    confirmLabel: String,
    initialPath: List<PickerLevel>,
    storages: List<UsbStorage>,
    loadFolders: suspend (FolderLocation) -> List<FileItem>,
    onPick: (FolderLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val stack = remember { mutableStateListOf<PickerLevel>().apply { addAll(initialPath) } }
    val current = stack.lastOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { stack.removeLastOrNull() }, enabled = stack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Subir un nivel")
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (stack.isEmpty()) "Elige una memoria" else stack.joinToString(" › ") { it.name },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                if (current == null) {
                    LazyColumn {
                        items(storages, key = { it.id }) { storage ->
                            val access = storage.access ?: return@items
                            ListItem(
                                headlineContent = { Text(storage.label) },
                                leadingContent = {
                                    Icon(if (storage.kind == StorageKind.SD_CARD) Icons.Outlined.SdCard else Icons.Outlined.Usb, null)
                                },
                                modifier = Modifier.clickable {
                                    stack.add(PickerLevel(FolderLocation(storage.id, access.treeUri, access.rootDocumentId), storage.label))
                                },
                            )
                        }
                    }
                } else {
                    val folders by produceState<List<FileItem>?>(null, current.location) {
                        value = runCatching { loadFolders(current.location) }.getOrDefault(emptyList())
                    }
                    when {
                        folders == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        folders!!.isEmpty() -> Text(
                            "Sin subcarpetas",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn {
                            items(folders!!, key = { it.documentId }) { folder ->
                                ListItem(
                                    headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                                    leadingContent = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable {
                                        stack.add(PickerLevel(current.location.copy(documentId = folder.documentId), folder.name))
                                    },
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = { current?.let { onPick(it.location) } }, enabled = current != null) {
                    Text(if (current == null) confirmLabel else "$confirmLabel aquí")
                }
            }
        }
    }
}
