package com.mcifu.usbx.data.thumbnail

import android.net.Uri
import coil3.key.Keyer
import coil3.request.Options
import com.mcifu.usbx.data.storage.contentUri
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileType

/**
 * Petición de miniatura para Coil. Se distingue de un `Uri` normal para que Coil use
 * [ThumbnailFetcher] (miniatura EXIF, fotograma de vídeo, carátula…) en lugar de decodificar
 * el archivo completo.
 */
data class Thumbnail(
    val uri: Uri,
    val type: FileType,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
) {
    /** Cambia si el archivo cambia: invalida automáticamente cachés antiguas. */
    val cacheKey: String get() = "thumb:$uri:$size:$lastModified"
}

fun FileItem.toThumbnail() = Thumbnail(contentUri, type, mimeType, size, lastModified)

val FileType.hasThumbnail: Boolean
    get() = this == FileType.IMAGE || this == FileType.VIDEO || this == FileType.AUDIO

class ThumbnailKeyer : Keyer<Thumbnail> {
    override fun key(data: Thumbnail, options: Options): String = data.cacheKey
}
