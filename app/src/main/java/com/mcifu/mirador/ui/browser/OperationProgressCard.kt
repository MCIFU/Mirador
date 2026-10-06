package com.mcifu.mirador.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.FileOperationRules
import com.mcifu.mirador.domain.model.OperationKind
import com.mcifu.mirador.domain.model.OperationProgress
import com.mcifu.mirador.domain.model.OperationStatus
import com.mcifu.mirador.ui.common.Formatters

/** Tarjeta de progreso de copiar/mover/eliminar, con cancelar mientras dura. */
@Composable
fun OperationProgressCard(
    progress: OperationProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val verb = when (progress.kind) {
        OperationKind.COPY -> "Copiando"
        OperationKind.MOVE -> "Moviendo"
        OperationKind.DELETE -> "Eliminando"
    }
    val title = when (progress.status) {
        OperationStatus.PREPARING -> "Preparando…"
        OperationStatus.RUNNING -> "$verb ${progress.doneItems + 1} de ${progress.totalItems}"
        OperationStatus.DONE -> when (progress.kind) {
            OperationKind.COPY -> "Copia terminada"
            OperationKind.MOVE -> "Movimiento terminado"
            OperationKind.DELETE -> "Eliminación terminada"
        } + " · ${progress.totalItems} ${if (progress.totalItems == 1) "elemento" else "elementos"}"
        OperationStatus.FAILED -> progress.message ?: "La operación no se completó"
        OperationStatus.CANCELLED -> "Cancelado. Lo ya terminado se conserva; lo que estaba a medias se ha borrado."
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.status == OperationStatus.FAILED) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!progress.isFinished) {
                val fraction = FileOperationRules.fraction(progress.doneBytes, progress.totalBytes, progress.doneItems, progress.totalItems)
                if (progress.status == OperationStatus.PREPARING) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
                val detail = buildList {
                    progress.currentName?.let { add(it) }
                    if (progress.totalBytes > 0) add("${Formatters.size(context, progress.doneBytes)} de ${Formatters.size(context, progress.totalBytes)}")
                }.joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            } else if (progress.failedNames.isNotEmpty()) {
                Text(
                    "No procesados: " + progress.failedNames.take(5).joinToString(", ") + if (progress.failedNames.size > 5) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (progress.isFinished) TextButton(onClick = onDismiss) { Text("Cerrar") }
                else TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}
