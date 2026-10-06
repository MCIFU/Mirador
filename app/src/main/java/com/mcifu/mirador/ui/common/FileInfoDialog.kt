package com.mcifu.mirador.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.model.FileDetails
import com.mcifu.mirador.domain.model.FileItem

/** Diálogo "Información". Los datos lentos (dimensiones, duración) se cargan en segundo plano. */
@Composable
fun FileInfoDialog(
    item: FileItem,
    loadDetails: suspend (FileItem) -> FileDetails,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val details by produceState<FileDetails?>(initialValue = null, item) { value = loadDetails(item) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Información") },
        text = {
            SelectionContainer {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InfoRow("Nombre", item.name)
                    InfoRow("Tipo", "${item.type.label()} (${item.mimeType})")
                    if (!item.isDirectory) {
                        InfoRow("Tamaño", "${Formatters.size(context, item.size)} (${"%,d".format(item.size)} bytes)")
                    }
                    InfoRow("Modificado", Formatters.date(item.lastModified))
                    val d = details
                    if (d == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    } else {
                        InfoRow("Almacenamiento", d.storageLabel)
                        InfoRow("Ruta", d.path)
                        if (d.width != null && d.height != null) {
                            InfoRow("Dimensiones", "${d.width} × ${d.height} px (${megapixels(d.width, d.height)})")
                        }
                        d.durationMs?.let { InfoRow("Duración", Formatters.duration(it)) }
                        d.extra.forEach { (label, value) -> InfoRow(label, value) }
                    }
                }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun megapixels(width: Int, height: Int): String = "%.1f MP".format(width * height / 1_000_000.0)
