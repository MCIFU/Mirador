package com.mcifu.usbx.data.thumbnail

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Integra [ThumbnailGenerator] en Coil con dos niveles de caché:
 *
 * 1. Memoria (gestionada por Coil con [ThumbnailKeyer]): volver a una carpeta es instantáneo.
 * 2. Disco (caché de Coil en `cacheDir`): la segunda vez que conectas el mismo pendrive, las
 *    miniaturas ya generadas se leen del móvil y no de la memoria USB.
 *
 * El tamaño pedido se redondea a unos pocos "cubos" para reutilizar la misma miniatura entre
 * tamaños de cuadrícula parecidos.
 */
class ThumbnailFetcher(
    private val data: Thumbnail,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val generator: ThumbnailGenerator,
    private val dispatcher: CoroutineDispatcher,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val requested = maxOf(
            options.size.width.pxOrElse { DEFAULT_TARGET },
            options.size.height.pxOrElse { DEFAULT_TARGET },
        )
        val target = bucketFor(requested)
        val diskKey = "${data.cacheKey}@$target"
        val diskCache = imageLoader.diskCache

        diskCache?.openSnapshot(diskKey)?.let { snapshot ->
            return SourceFetchResult(
                source = ImageSource(
                    file = snapshot.data,
                    fileSystem = diskCache.fileSystem,
                    diskCacheKey = diskKey,
                    closeable = snapshot,
                ),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        }

        val bitmap = withContext(dispatcher) { generator.generate(data, target) }
            ?: throw IOException("No hay miniatura para ${data.uri}")

        if (diskCache != null) {
            withContext(dispatcher) {
                diskCache.openEditor(diskKey)?.let { editor ->
                    try {
                        diskCache.fileSystem.write(editor.data) {
                            val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            bitmap.compress(format, 85, outputStream())
                        }
                        editor.commit()
                    } catch (_: Exception) {
                        editor.abort()
                    }
                }
            }
        }

        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val generator: ThumbnailGenerator,
        private val dispatcher: CoroutineDispatcher,
    ) : Fetcher.Factory<Thumbnail> {
        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, options, imageLoader, generator, dispatcher)
    }

    companion object {
        private const val DEFAULT_TARGET = 320
        private val BUCKETS = intArrayOf(160, 256, 384, 512, 768)

        fun bucketFor(requestedPx: Int): Int =
            BUCKETS.firstOrNull { it >= requestedPx * 0.9f } ?: BUCKETS.last()
    }
}
