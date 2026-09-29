package com.mcifu.usbx.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mcifu.usbx.data.thumbnail.hasThumbnail
import com.mcifu.usbx.data.thumbnail.toThumbnail
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileType
import com.mcifu.usbx.ui.common.icon
import com.mcifu.usbx.ui.common.tint

/**
 * Miniatura de un archivo con el icono de su tipo debajo como marcador de posición.
 * Si no se puede generar miniatura (formato no soportado, archivo dañado), queda el icono.
 * La imagen se carga bajo demanda: Coil cancela la petición si la celda sale de pantalla.
 */
@Composable
fun FileThumbnail(
    item: FileItem,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    showVideoBadge: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    var loaded by remember(item.uri) { mutableStateOf(false) }
    Box(modifier.background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Icon(
                imageVector = item.type.icon(),
                contentDescription = null,
                tint = item.type.tint(colors),
                modifier = Modifier.size(iconSize),
            )
        }
        if (item.type.hasThumbnail) {
            val context = LocalContext.current
            val request = remember(item.uri, item.lastModified, item.size) {
                val thumbnail = item.toThumbnail()
                // Clave fija por archivo: la lista, la cuadrícula, la tira del visor y el propio
                // visor (como imagen provisional) comparten la misma miniatura en memoria.
                ImageRequest.Builder(context).data(thumbnail).memoryCacheKey(thumbnail.cacheKey).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showVideoBadge && item.type == FileType.VIDEO) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
