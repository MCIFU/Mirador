package com.mcifu.mirador.data.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
    private val failures: LruCache<String, Boolean>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val requested = maxOf(
            options.size.width.pxOrElse { DEFAULT_TARGET },
            options.size.height.pxOrElse { DEFAULT_TARGET },
        )
        val target = bucketFor(requested)
        val diskKey = "${data.cacheKey}@$target"
        val diskCache = imageLoader.diskCache

        // Caché de disco (almacenamiento interno, rápido): se decodifica aquí y se marca siempre
        // como reducida (isSampled) para que Coil no reutilice una miniatura pequeña en una celda
        // más grande: todas las vistas comparten la misma clave de memoria por archivo.
        if (diskCache != null) {
            val cached = withContext(Dispatchers.IO) {
                diskCache.openSnapshot(diskKey)?.use { snapshot -> BitmapFactory.decodeFile(snapshot.data.toFile().path, hardwareOptions()) }
            }
            if (cached != null) {
                return ImageFetchResult(image = cached.asImage(), isSampled = true, dataSource = DataSource.DISK)
            }
        }

        // Archivos sin miniatura posible (MP3 sin carátula, vídeo con códec no soportado…):
        // se recuerdan para no volver a leerlos del USB cada vez que pasan por pantalla.
        if (failures.get(data.cacheKey) != null) throw IOException("Sin miniatura (en caché)")

        val bitmap = withContext(dispatcher) { generator.generate(data, target) }
        if (bitmap == null) {
            failures.put(data.cacheKey, true)
            throw IOException("No hay miniatura para ${data.uri}")
        }

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

        // Bitmap en GPU: al desplazarse no hay que subir la textura en el hilo de dibujo.
        val drawable = bitmap.copy(Bitmap.Config.HARDWARE, false)?.also { bitmap.recycle() } ?: bitmap
        return ImageFetchResult(
            image = drawable.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    /**
     * @param failures archivos sin miniatura posible. Se vacía al cambiar las memorias conectadas,
     * porque un fallo durante una desconexión no significa que el archivo no tenga miniatura.
     */
    class Factory(
        private val generator: ThumbnailGenerator,
        private val dispatcher: CoroutineDispatcher,
        private val failures: LruCache<String, Boolean>,
    ) : Fetcher.Factory<Thumbnail> {

        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(data, options, imageLoader, generator, dispatcher, failures)
    }

    companion object {
        private const val DEFAULT_TARGET = 320
        private val BUCKETS = intArrayOf(160, 256, 384, 512, 768)

        /** Las miniaturas solo se dibujan: se decodifican directamente como bitmap de GPU. */
        private fun hardwareOptions() = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }

        fun bucketFor(requestedPx: Int): Int =
            BUCKETS.firstOrNull { it >= requestedPx * 0.9f } ?: BUCKETS.last()
    }
}
