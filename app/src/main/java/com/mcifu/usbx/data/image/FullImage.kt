package com.mcifu.usbx.data.image

import android.content.Context
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.Build
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.mcifu.usbx.domain.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Imagen que debe mostrarse entera, sin subsampling. Coil la decodifica igual que un `Uri`,
 * pero como no es un `Uri` el visor ampliable no intenta leerla por teselas.
 */
data class FullImage(val uri: Uri, val mimeType: String)

class FullImageFetcher(
    private val data: FullImage,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val stream = options.context.contentResolver.openInputStream(data.uri)
            ?: throw FileNotFoundException("No se puede abrir ${data.uri}")
        return SourceFetchResult(
            source = ImageSource(source = stream.source().buffer(), fileSystem = FileSystem.SYSTEM),
            mimeType = data.mimeType,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<FullImage> {
        override fun create(data: FullImage, options: Options, imageLoader: ImageLoader): Fetcher =
            FullImageFetcher(data, options)
    }
}

class FullImageKeyer : Keyer<FullImage> {
    override fun key(data: FullImage, options: Options): String = "full:${data.uri}"
}

/**
 * ¿Puede Android decodificar esta imagen por regiones (necesario para el zoom nítido)?
 *
 * JPEG, PNG y WebP siempre; GIF lo gestiona el propio visor. Para el resto (HEIC, BMP…)
 * depende del formato y del móvil, así que se prueba una vez por archivo abriendo un
 * `BitmapRegionDecoder`, que solo lee la cabecera.
 */
object RegionDecoding {
    private val alwaysSupported = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    private val cache = ConcurrentHashMap<String, Boolean>()

    /** Respuesta inmediata si se conoce sin leer el archivo. */
    fun knownSupport(item: FileItem): Boolean? =
        if (item.mimeType in alwaysSupported) true else cache[item.uri]

    suspend fun supports(context: Context, item: FileItem, uri: Uri): Boolean {
        knownSupport(item)?.let { return it }
        val supported = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BitmapRegionDecoder.newInstance(pfd)
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapRegionDecoder.newInstance(pfd.fileDescriptor, false)
                    }
                    decoder?.recycle()
                    decoder != null
                } ?: false
            } catch (_: Exception) {
                false
            }
        }
        cache[item.uri] = supported
        return supported
    }
}
