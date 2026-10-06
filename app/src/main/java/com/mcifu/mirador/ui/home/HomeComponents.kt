package com.mcifu.mirador.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.model.MountState
import com.mcifu.mirador.domain.model.StorageKind
import com.mcifu.mirador.domain.model.UsbStorage
import com.mcifu.mirador.ui.common.Formatters

/**
 * Memoria lista para explorar: tarjeta destacada con el degradado de la marca, el espacio
 * usado y un único botón. Toda la tarjeta abre la memoria.
 */
@Composable
internal fun ReadyStorageCard(storage: UsbStorage, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(listOf(colors.primary, lerp(colors.primary, Color.Black, 0.45f)))
    val onCard = Color.White
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.background(gradient).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = onCard.copy(alpha = 0.16f), modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(storage.icon(), contentDescription = null, tint = onCard, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        storage.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (storage.mountState == MountState.READ_ONLY) "Conectada · solo lectura" else "Conectada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onCard.copy(alpha = 0.8f),
                    )
                }
            }
            val total = storage.totalBytes
            val free = storage.freeBytes
            if (total != null && free != null && total > 0) {
                val context = LocalContext.current
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                    color = onCard,
                    trackColor = onCard.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Formatters.size(context, free)} libres de ${Formatters.size(context, total)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = onCard.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onOpen,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = onCard, contentColor = colors.primary),
                ) {
                    Text("Explorar")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Memoria que aún no se puede abrir: falta acceso, está comprobándose o se desconectó. */
@Composable
internal fun StorageCard(
    storage: UsbStorage,
    onRequestAccess: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (storage.isAvailable) colors.primaryContainer else colors.surfaceVariant,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            storage.icon(),
                            contentDescription = null,
                            tint = if (storage.isAvailable) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(storage.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(statusText(storage), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            if (storage.mountState == MountState.CHECKING) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                storage.isAvailable && !storage.isAuthorized -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Android pide que confirmes una vez el acceso a esta memoria.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) { Text("Conceder acceso") }
                }
                storage.isAuthorized && !storage.isAvailable -> {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onForget) { Text("Olvidar") }
                    }
                }
            }
        }
    }
}

/** Sin memorias: icono con un pulso suave mientras Mirador espera a que Android monte una. */
@Composable
internal fun WaitingForStorage(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            // Ondas que se expanden y se desvanecen (dibujadas, sin recomponer en cada fotograma).
            Box(
                Modifier.size(180.dp).drawBehind {
                    for (i in 0..1) {
                        val t = (pulse + i * 0.5f) % 1f
                        drawCircle(colors.primary.copy(alpha = 0.22f * (1f - t)), radius = size.minDimension / 2f * (0.55f + 0.45f * t))
                    }
                },
            )
            Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(96.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Usb, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(48.dp))
                }
            }
        }
        Text(
            "Conecta una memoria",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Pendrive USB‑C, adaptador OTG o tarjeta SD. Aparecerá aquí en cuanto Android la detecte.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 340.dp),
        )
    }
}

/** Accesos secundarios: abrir otra carpeta del móvil y las carpetas guardadas (con candado). */
@Composable
internal fun QuickActions(
    onPickFolder: () -> Unit,
    onSavedFolders: () -> Unit,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            icon = Icons.Outlined.CreateNewFolder,
            title = "Abrir carpeta",
            subtitle = "Del móvil o la SD",
            onClick = onPickFolder,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            icon = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            title = "Guardadas",
            subtitle = if (locked) "Protegidas" else "Desbloqueadas",
            onClick = onSavedFolders,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

internal fun UsbStorage.icon(): ImageVector = when {
    !isAvailable -> Icons.Outlined.UsbOff
    kind == StorageKind.SD_CARD -> Icons.Outlined.SdCard
    else -> Icons.Outlined.Usb
}

private fun statusText(storage: UsbStorage): String = when (storage.mountState) {
    MountState.CHECKING -> "Comprobando la memoria…"
    MountState.UNAVAILABLE -> if (storage.isAuthorized) "Desconectada · acceso guardado" else "No disponible"
    MountState.READ_ONLY -> if (storage.isAuthorized) "Conectada · solo lectura" else "Conectada · sin acceso"
    MountState.MOUNTED -> if (storage.isAuthorized) "Conectada" else "Conectada · falta conceder acceso"
}
