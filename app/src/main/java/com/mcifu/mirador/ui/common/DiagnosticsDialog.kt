package com.mcifu.mirador.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.domain.model.StorageKind
import com.mcifu.mirador.domain.model.UsbStorage

/** Informe de diagnóstico: copiar o compartir para enviarlo al desarrollador. */
@Composable
fun DiagnosticsDialog(storages: List<UsbStorage>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(Diagnostics.report(context, storageSummary(storages))) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Informe de diagnóstico") },
        text = {
            Column {
                Text(
                    "Recoge qué pasa al abrir archivos y los errores de lectura o reproducción. No incluye tus fotos ni vídeos, solo nombres de archivo. Cópialo y pégalo en el chat.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { copyToClipboard(context, report); copied = true }) {
                    Text(if (copied) "Copiado" else "Copiar")
                }
                TextButton(onClick = { share(context, report) }) { Text("Compartir") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    Diagnostics.clear()
                    report = Diagnostics.report(context, storageSummary(storages))
                }) { Text("Borrar") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

/** Aviso al arrancar si la app se cerró inesperadamente la vez anterior. */
@Composable
fun CrashNoticeDialog(onShowReport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mirador se cerró inesperadamente") },
        text = { Text("Se ha guardado un informe del error. Si lo compartes, se puede corregir.") },
        confirmButton = { TextButton(onClick = onShowReport) { Text("Ver informe") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}

private fun storageSummary(storages: List<UsbStorage>): String =
    if (storages.isEmpty()) "Ninguno detectado"
    else storages.joinToString("\n") { s ->
        // Las carpetas guardadas son privadas: el informe no incluye su nombre.
        val name = if (s.kind == StorageKind.FOLDER) "(carpeta guardada)" else s.label
        "$name | ${s.kind} | ${s.mountState} | acceso: ${if (s.isAuthorized) s.access?.treeUri?.authority else "no"}"
    }

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Informe Mirador", text))
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Informe de diagnóstico Mirador")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Compartir informe")) }
}
