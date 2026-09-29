package com.mcifu.usbx.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Se muestra cuando ninguna app acepta el tipo del archivo. Nunca provoca un cierre de la app. */
@Composable
fun CannotOpenDialog(
    fileName: String,
    canOpenGeneric: Boolean,
    onOpenWithOther: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Block, contentDescription = null) },
        title = { Text("No se puede abrir este archivo directamente") },
        text = {
            Text(
                if (canOpenGeneric) "Ninguna app declara compatibilidad con «$fileName». Puedes probar con otra aplicación instalada."
                else "No hay ninguna aplicación instalada que pueda abrir «$fileName».",
            )
        },
        confirmButton = {
            if (canOpenGeneric) {
                TextButton(onClick = onOpenWithOther) { Text("Abrir con otra aplicación") }
            } else {
                TextButton(onClick = onDismiss) { Text("Aceptar") }
            }
        },
        dismissButton = if (canOpenGeneric) {
            { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        } else null,
    )
}
