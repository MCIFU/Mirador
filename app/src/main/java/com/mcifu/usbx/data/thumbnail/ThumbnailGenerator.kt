package com.mcifu.usbx.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.mcifu.usbx.domain.model.FileType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Genera miniaturas leyendo lo mínimo posible de la memoria USB.
 *
 * - Fotos: primero la miniatura EXIF incrustada (unos KB al principio del archivo; las cámaras
 *   Samsung guardan 512×384). Solo si no existe o es pequeña se decodifica la imagen con
 *   submuestreo, que nunca carga la foto completa en memoria.
 * - Vídeos: un fotograma clave cercano al segundo 1 con MediaMetadataRetriever.
 * - Audio: la carátula incrustada si la hay.
 *
 * Todas las funciones son bloqueantes: se llaman desde un dispatcher de E/S limitado.
 * Devuelven `null` si no hay miniatura posible; nunca lanzan excepciones.
 */
class ThumbnailGenerator(private val context: Context) {

    /** @param targetPx longitud mínima del lado corto del resultado (para recorte centrado). */
    fun generate(thumbnail: Thumbnail, targetPx: Int): Bitmap? = try {
        when (thumbnail.type) {
            FileType.IMAGE -> imageThumbnail(thumbnail.uri, thumbnail.mimeType, targetPx)
            FileType.VIDEO -> videoThumbnail(thumbnail.uri, targetPx)
            FileType.AUDIO -> audioThumbnail(thumbnail.uri, targetPx)
            else -> null
        }?.let { scaleToShortSide(it, targetPx) }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    // --- Imágenes --------------------------------------------------------------------------

    private fun imageThumbnail(uri: Uri, mimeType: String, targetPx: Int): Bitmap? {
        if (mimeType in EXIF_THUMBNAIL_MIMES) {
            exifThumbnail(uri, targetPx)?.let { return it }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(uri, targetPx)
        } else {
            decodeWithBitmapFactory(uri, targetPx)
        }
    }

    private fun exifThumbnail(uri: Uri, targetPx: Int): Bitmap? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return pfd.use {
            val exif = ExifInterface(it.fileDescriptor)
            if (!exif.hasThumbnail()) return null
            val thumb = exif.thumbnailBitmap ?: return null
            val tooSmall = min(thumb.width, thumb.height) < targetPx * 0.75f
            // Algunas cámaras añaden bandas negras a la miniatura si su proporción no coincide
            // con la de la foto: en ese caso se descarta para no mostrar recortes feos.
            val imageWidth = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)
                .takeIf { w -> w > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val imageHeight = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)
                .takeIf { h -> h > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            val aspectMismatch = imageWidth > 0 && imageHeight > 0 &&
                abs(thumb.width.toFloat() / thumb.height - imageWidth.toFloat() / imageHeight) > 0.05f
            if (tooSmall || aspectMismatch) {
                thumb.recycle()
                return null
            }
            rotate(thumb, exif.rotationDegrees)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri, targetPx: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder aplica la orientación EXIF automáticamente.
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height, targetPx))
        }
    }

    private fun decodeWithBitmapFactory(uri: Uri, targetPx: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        return rotate(bitmap, rotation)
    }

    // --- Vídeo y audio -----------------------------------------------------------------------

    private fun videoThumbnail(uri: Uri, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // El primer fotograma suele ser negro (fundido); el segundo 1 es más representativo.
            val timeUs = if (durationMs > 0) min(1_000L, durationMs / 3) * 1_000 else 0L
            val box = targetPx * 2
            fun frameAt(us: Long): Bitmap? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, box, box)
                } else {
                    retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            return frameAt(timeUs) ?: if (timeUs != 0L) frameAt(0L) else null
        } finally {
            retriever.release()
        }
    }

    private fun audioThumbnail(uri: Uri, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(art, 0, art.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
            }
            return BitmapFactory.decodeByteArray(art, 0, art.size, options)
        } finally {
            retriever.release()
        }
    }

    // --- Utilidades --------------------------------------------------------------------------

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Reduce para que el lado corto mida [targetPx]: menos memoria y menos disco en caché. */
    private fun scaleToShortSide(bitmap: Bitmap, targetPx: Int): Bitmap {
        val shortSide = min(bitmap.width, bitmap.height)
        if (shortSide <= targetPx) return bitmap
        val scale = targetPx.toFloat() / shortSide
        val scaled = bitmap.scale(
            max(1, (bitmap.width * scale).roundToInt()),
            max(1, (bitmap.height * scale).roundToInt()),
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    companion object {
        private val EXIF_THUMBNAIL_MIMES = setOf("image/jpeg", "image/heic", "image/heif", "image/webp")

        /** Mayor potencia de 2 que mantiene el lado corto ≥ [targetPx]. */
        fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
            val shortSide = min(width, height)
            if (shortSide <= 0 || targetPx <= 0) return 1
            var sample = 1
            while (shortSide / (sample * 2) >= targetPx) sample *= 2
            return sample
        }
    }
}
