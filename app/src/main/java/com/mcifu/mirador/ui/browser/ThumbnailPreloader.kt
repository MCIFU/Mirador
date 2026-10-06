package com.mcifu.mirador.ui.browser

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.mcifu.mirador.data.thumbnail.hasThumbnail
import com.mcifu.mirador.data.thumbnail.toThumbnail
import com.mcifu.mirador.domain.model.FileItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Prepara en memoria las miniaturas de las filas que están a punto de entrar en pantalla, en el
 * sentido del desplazamiento. Usa la misma clave y tamaño que [FileThumbnail], así que al llegar
 * la celda la imagen ya está decodificada y no aparece primero el icono.
 */
@Composable
fun PreloadGridThumbnails(
    items: List<FileItem>,
    state: LazyGridState,
    columns: Int,
    cellPx: Int,
) {
    val context = LocalContext.current
    LaunchedEffect(items, columns, cellPx) {
        if (cellPx <= 0) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        var previousFirst = state.firstVisibleItemIndex
        snapshotFlow {
            val visible = state.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: -1)
        }
            .distinctUntilChanged()
            .collectLatest { (first, last) ->
                val forward = first >= previousFirst
                previousFirst = first
                // Espera breve: durante un desplazamiento muy rápido no se encolan filas que se saltan.
                delay(PRELOAD_DELAY_MS)
                val ahead = columns * PRELOAD_ROWS
                val range = if (forward) (last + 1)..(last + ahead) else (first - ahead) until first
                range.mapNotNull { items.getOrNull(it) }
                    .filter { it.type.hasThumbnail }
                    .forEach { item ->
                        val thumbnail = item.toThumbnail()
                        loader.enqueue(
                            ImageRequest.Builder(context)
                                .data(thumbnail)
                                .memoryCacheKey(thumbnail.cacheKey)
                                .size(cellPx)
                                .build(),
                        )
                    }
            }
    }
}

private const val PRELOAD_ROWS = 3
private const val PRELOAD_DELAY_MS = 60L
