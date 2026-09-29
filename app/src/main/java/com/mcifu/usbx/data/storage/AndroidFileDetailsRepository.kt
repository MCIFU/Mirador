package com.mcifu.usbx.data.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.mcifu.usbx.domain.model.FileDetails
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileType
import com.mcifu.usbx.domain.repository.FileDetailsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFileDetailsRepository(
    private val context: Context,
) : FileDetailsRepository {

    override suspend fun loadDetails(item: FileItem, storageLabel: String, parentPath: List<String>): FileDetails =
        withContext(Dispatchers.IO) {
            val base = FileDetails(item = item, storageLabel = storageLabel, path = pathOf(item, parentPath))
            try {
                when (item.type) {
                    FileType.IMAGE -> imageDetails(base)
                    FileType.VIDEO, FileType.AUDIO -> mediaDetails(base)
                    else -> base
                }
            } catch (_: Exception) {
                base
            }
        }

    /**
     * Ruta legible. Con el proveedor de almacenamiento externo el documentId ya es
     * "UUID:DCIM/Camera/IMG.jpg"; si no, se reconstruye con los nombres de la navegación.
     */
    private fun pathOf(item: FileItem, parentPath: List<String>): String {
        val docId = item.documentId
        return if (':' in docId && !docId.startsWith("content")) {
            "/" + docId.substringAfter(':')
        } else {
            (parentPath.drop(1) + item.name).joinToString("/", prefix = "/")
        }
    }

    private fun imageDetails(base: FileDetails): FileDetails {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(base.item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var width = bounds.outWidth.takeIf { it > 0 }
        var height = bounds.outHeight.takeIf { it > 0 }
        val extra = mutableListOf<Pair<String, String>>()

        runCatching {
            resolver.openFileDescriptor(base.item.uri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                if (exif.rotationDegrees == 90 || exif.rotationDegrees == 270) {
                    width = height.also { height = width }
                }
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { extra += "Fecha de captura" to prettyExifDate(it) }
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                val camera = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
                if (camera.isNotEmpty()) extra += "Cámara" to camera
                exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { extra += "Apertura" to "f/%.1f".format(it) }
                exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                    extra += "Exposición" to if (it < 1) "1/${Math.round(1 / it)} s" else "%.1f s".format(it)
                }
                exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { extra += "ISO" to it }
            }
        }
        return base.copy(width = width, height = height, extra = extra)
    }

    private fun mediaDetails(base: FileDetails): FileDetails {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, base.item.uri)
            fun meta(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }

            val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            var width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            var height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) width = height.also { height = width }

            val extra = mutableListOf<Pair<String, String>>()
            meta(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { extra += "Título" to it }
            meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { extra += "Artista" to it }
            meta(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { extra += "Álbum" to it }
            meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                extra += "Tasa de bits" to "${it / 1000} kbps"
            }
            return base.copy(
                width = if (base.item.type == FileType.VIDEO) width else null,
                height = if (base.item.type == FileType.VIDEO) height else null,
                durationMs = duration,
                extra = extra,
            )
        } finally {
            retriever.release()
        }
    }

    /** "2024:03:12 18:45:03" → "12/03/2024 18:45". */
    private fun prettyExifDate(raw: String): String {
        val match = Regex("""(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2})""").find(raw) ?: return raw
        val (y, mo, d, h, mi) = match.destructured
        return "$d/$mo/$y $h:$mi"
    }
}
