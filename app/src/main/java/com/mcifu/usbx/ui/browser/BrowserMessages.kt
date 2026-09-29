package com.mcifu.usbx.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcifu.usbx.domain.model.StorageException

@Composable
internal fun EmptyFolder(hiddenCount: Int = 0) {
    CenteredMessage(
        icon = { Icon(Icons.Outlined.FolderOff, null, Modifier.size(48.dp)) },
        title = "Carpeta vacía",
        body = if (hiddenCount > 0) "Hay elementos ocultos. Actívalos en Visualización." else null,
    )
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
        icon = {
            Icon(
                if (reason == StorageException.Reason.DISCONNECTED) Icons.Outlined.UsbOff else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = title,
        body = body,
        action = { Button(onClick = onRetry) { Text("Reintentar") } },
    )
}

@Composable
internal fun CenteredMessage(
    icon: @Composable () -> Unit,
    title: String,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    // Desplazable para que el gesto de "tirar para actualizar" funcione también aquí.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
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
