package com.mcifu.mirador.ui.multiview

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.ui.browser.FileThumbnail

/** Selector de la foto o vídeo de un panel, entre los de la misma carpeta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerSheet(
    media: List<FileItem>,
    selectedDocumentId: String?,
    onPick: (FileItem) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            Text(
                "Elige una foto o un vídeo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (media.isEmpty()) {
                Text("No hay fotos ni vídeos en esta carpeta.", modifier = Modifier.padding(20.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(media, key = { it.documentId }) { item ->
                    val selected = item.documentId == selectedDocumentId
                    FileThumbnail(
                        item = item,
                        iconSize = 32.dp,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                else Modifier,
                            )
                            .clickable { onPick(item) }
                            .semantics { contentDescription = item.name },
                    )
                }
            }
        }
    }
}
