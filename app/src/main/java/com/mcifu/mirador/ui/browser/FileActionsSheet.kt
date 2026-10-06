package com.mcifu.mirador.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.ui.common.Formatters
import com.mcifu.mirador.ui.common.label

enum class FileAction { OPEN, OPEN_WITH, MULTIVIEW, INFO, SHARE, RENAME, COPY, MOVE, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(
    item: FileItem,
    canWrite: Boolean,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                FileThumbnail(
                    item = item,
                    iconSize = 28.dp,
                    showVideoBadge = false,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                    Text(
                        if (item.isDirectory) item.type.label()
                        else "${item.type.label()} · ${Formatters.size(context, item.size)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ActionRow(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir") { onAction(FileAction.OPEN) }
            if (!item.isDirectory) {
                ActionRow(Icons.Outlined.Apps, "Abrir con…") { onAction(FileAction.OPEN_WITH) }
            }
            if (item.type.isVisualMedia) {
                ActionRow(Icons.Outlined.ViewAgenda, "Comparar en multiview") { onAction(FileAction.MULTIVIEW) }
            }
            ActionRow(Icons.Outlined.Info, "Información") { onAction(FileAction.INFO) }
            if (!item.isDirectory) {
                ActionRow(Icons.Outlined.Share, "Compartir") { onAction(FileAction.SHARE) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ActionRow(Icons.Outlined.ContentCopy, "Copiar a…") { onAction(FileAction.COPY) }
            if (canWrite) {
                ActionRow(Icons.Outlined.DriveFileMove, "Mover a…") { onAction(FileAction.MOVE) }
                ActionRow(Icons.Outlined.DriveFileRenameOutline, "Renombrar") { onAction(FileAction.RENAME) }
                ActionRow(Icons.Outlined.Delete, "Eliminar") { onAction(FileAction.DELETE) }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}
