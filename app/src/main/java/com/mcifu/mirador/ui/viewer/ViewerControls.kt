package com.mcifu.mirador.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.OrientationMode
import com.mcifu.mirador.domain.model.ViewerScope
import com.mcifu.mirador.ui.browser.FileThumbnail

@Composable
internal fun ViewerTopBar(
    title: String,
    counter: String,
    showFilmstrip: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onChooseScope: () -> Unit,
    onToggleFilmstrip: () -> Unit,
    onMultiview: (() -> Unit)?,
    onDiagnostics: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .statusBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al explorador", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.titleMedium)
                Text(counter, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, contentDescription = "Información", tint = Color.White) }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Compartir", tint = Color.White) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Abrir con…") },
                        leadingIcon = { Icon(Icons.Outlined.Apps, null) },
                        onClick = { menuOpen = false; onOpenWith() },
                    )
                    if (onMultiview != null) {
                        DropdownMenuItem(
                            text = { Text("Comparar en multiview") },
                            leadingIcon = { Icon(Icons.Outlined.ViewAgenda, null) },
                            onClick = { menuOpen = false; onMultiview() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Qué recorrer al deslizar…") },
                        leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                        onClick = { menuOpen = false; onChooseScope() },
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Informe de diagnóstico") },
                        leadingIcon = { Icon(Icons.Outlined.BugReport, null) },
                        onClick = { menuOpen = false; onDiagnostics() },
                    )
                    DropdownMenuItem(
                        text = { Text("Tira de miniaturas") },
                        leadingIcon = { Icon(Icons.Outlined.ViewCarousel, null) },
                        trailingIcon = { Checkbox(checked = showFilmstrip, onCheckedChange = null) },
                        onClick = { menuOpen = false; onToggleFilmstrip() },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ViewerBottomBar(
    items: List<FileItem>,
    currentIndex: Int,
    showFilmstrip: Boolean,
    canRotate: Boolean,
    orientation: OrientationMode,
    onSelect: (Int) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onCycleOrientation: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .navigationBarsPadding()
            .padding(top = 24.dp, bottom = 4.dp),
    ) {
        if (showFilmstrip && items.size > 1) {
            Filmstrip(items, currentIndex, onSelect)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarAction(Icons.AutoMirrored.Outlined.RotateLeft, "Girar a la izquierda", enabled = canRotate, onClick = onRotateLeft)
            BarAction(Icons.AutoMirrored.Outlined.RotateRight, "Girar a la derecha", enabled = canRotate, onClick = onRotateRight)
            val (icon, label) = when (orientation) {
                OrientationMode.AUTO -> Icons.Outlined.ScreenRotation to "Auto"
                OrientationMode.PORTRAIT -> Icons.Outlined.StayCurrentPortrait to "Vertical"
                OrientationMode.LANDSCAPE -> Icons.Outlined.StayCurrentLandscape to "Horizontal"
            }
            BarAction(icon, "Orientación: $label", label = label, onClick = onCycleOrientation)
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = description, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
        }
        if (label != null) {
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Tira de miniaturas: salta a cualquier elemento de la carpeta sin volver al explorador. */
@Composable
private fun Filmstrip(items: List<FileItem>, currentIndex: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
    val itemSize = 52.dp
    val itemPx = with(LocalDensity.current) { itemSize.roundToPx() }
    LaunchedEffect(currentIndex) {
        val viewport = listState.layoutInfo.viewportSize.width
        // Mantiene centrada la miniatura actual.
        listState.animateScrollToItem(currentIndex, scrollOffset = -(viewport / 2 - itemPx / 2))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.documentId }) { index, item ->
            val selected = index == currentIndex
            val scale by animateFloatAsState(if (selected) 1f else 0.86f, label = "filmstripScale")
            FileThumbnail(
                item = item,
                iconSize = 22.dp,
                modifier = Modifier
                    .size(itemSize)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.small)
                        else Modifier,
                    )
                    .clickable { onSelect(index) }
                    .semantics { contentDescription = "Ir a ${item.name}" },
            )
        }
    }
}

@Composable
internal fun ScopeDialog(
    current: ViewerScope,
    onSelect: (ViewerScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Qué recorrer al deslizar") },
        text = {
            Column {
                ViewerScope.entries.forEach { scope ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = scope == current, role = Role.RadioButton, onClick = { onSelect(scope) })
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = scope == current, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(scope.title(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                scope.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun ViewerScope.title() = when (this) {
    ViewerScope.IMAGES -> "Solo imágenes"
    ViewerScope.VISUAL_MEDIA -> "Fotos y vídeos"
    ViewerScope.VIDEOS -> "Solo vídeos"
    ViewerScope.ALL_MEDIA -> "Todo lo multimedia"
    ViewerScope.ALL_FILES -> "Todos los archivos"
}

private fun ViewerScope.description() = when (this) {
    ViewerScope.IMAGES -> "Salta vídeos, audio y documentos"
    ViewerScope.VISUAL_MEDIA -> "Recomendado para carpetas de cámara"
    ViewerScope.VIDEOS -> "Solo los vídeos de la carpeta"
    ViewerScope.ALL_MEDIA -> "Fotos, vídeos y audio"
    ViewerScope.ALL_FILES -> "Incluye documentos y otros archivos"
}
