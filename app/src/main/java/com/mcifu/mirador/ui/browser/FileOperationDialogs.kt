package com.mcifu.mirador.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.mcifu.mirador.domain.FileNameRules
import com.mcifu.mirador.domain.model.FileItem
import kotlinx.coroutines.launch

/**
 * Diálogo de nombre para "Nueva carpeta" y "Renombrar". Valida en vivo con las reglas de FAT32/exFAT
 * y al renombrar selecciona el nombre sin la extensión.
 */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    isDirectory: Boolean,
    existingNames: Collection<String>,
    onConfirm: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    var value by remember {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, FileNameRules.baseNameEnd(initialName, isDirectory))))
    }
    var serverError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val trimmed = value.text.trim()
    val validation = FileNameRules.validate(trimmed, existingNames, currentName = initialName.takeIf { it.isNotEmpty() })
    val unchanged = trimmed == initialName
    val error = serverError ?: validation?.takeIf { value.text.isNotEmpty() }?.let(FileNameRules::message)

    fun submit() {
        if (validation != null || unchanged || busy) return
        busy = true
        scope.launch {
            val result = onConfirm(trimmed)
            busy = false
            if (result == null) onDismiss() else serverError = result
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; serverError = null },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { if (error != null) Text(error) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = validation == null && !unchanged && !busy) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Confirmación de borrado: definitivo, sin papelera. */
@Composable
fun DeleteConfirmDialog(items: List<FileItem>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val folders = items.count { it.isDirectory }
    val subject = when {
        items.size == 1 -> "«${items.first().name}»"
        else -> "${items.size} elementos"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("¿Eliminar $subject?") },
        text = {
            Text(
                buildString {
                    append("Se borrará definitivamente de la memoria. Las memorias USB y tarjetas SD no tienen papelera: no se puede deshacer.")
                    if (folders > 0) append("\n\nLas carpetas se eliminan con todo su contenido.")
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
