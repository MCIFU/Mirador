package com.mcifu.mirador.di

import android.content.Context
import android.os.Build
import android.util.LruCache
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.mcifu.mirador.data.external.ExternalOpenResolver
import com.mcifu.mirador.data.image.FullImageFetcher
import com.mcifu.mirador.data.operations.SafFileOperations
import com.mcifu.mirador.data.image.FullImageKeyer
import com.mcifu.mirador.data.settings.DataStoreSettingsRepository
import com.mcifu.mirador.data.storage.AndroidFileDetailsRepository
import com.mcifu.mirador.data.storage.AndroidStorageRepository
import com.mcifu.mirador.data.storage.SafFileRepository
import com.mcifu.mirador.data.thumbnail.ThumbnailFetcher
import com.mcifu.mirador.data.thumbnail.ThumbnailGenerator
import com.mcifu.mirador.data.thumbnail.ThumbnailKeyer
import com.mcifu.mirador.domain.repository.FileDetailsRepository
import com.mcifu.mirador.domain.repository.FileOperations
import com.mcifu.mirador.domain.repository.FileRepository
import com.mcifu.mirador.domain.repository.SettingsRepository
import com.mcifu.mirador.domain.repository.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

/**
 * Inyección de dependencias manual. El grafo es pequeño y no justifica Hilt/Dagger:
 * menos tiempo de compilación y ninguna magia.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val storageRepository: StorageRepository = AndroidStorageRepository(appContext, appScope)

    val fileRepository: FileRepository = SafFileRepository(appContext)

    val fileDetailsRepository: FileDetailsRepository = AndroidFileDetailsRepository(appContext)

    val fileOperations: FileOperations by lazy {
        SafFileOperations(appContext, appScope, fileRepository, storageRepository)
    }

    val externalOpenResolver by lazy { ExternalOpenResolver(appContext, storageRepository, fileRepository) }

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext, appScope)

    /**
     * Generación de miniaturas limitada a 3 en paralelo: una memoria USB atiende las lecturas
     * de una en una por el bus, así que más paralelismo solo añade contención y retrasa las
     * miniaturas visibles.
     */
    private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(3)

    private val thumbnailFailures = LruCache<String, Boolean>(2_000)

    init {
        appScope.launch {
            storageRepository.storages
                .map { storages -> storages.filter { it.canBrowse }.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collect { thumbnailFailures.evictAll() }
        }
    }

    fun createImageLoader(): ImageLoader = ImageLoader.Builder(appContext)
        .components {
            // GIF, WebP y HEIF animados (Android 9+ decodifica con ImageDecoder).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            add(FullImageKeyer())
            add(FullImageFetcher.Factory())
            add(ThumbnailKeyer())
            add(ThumbnailFetcher.Factory(ThumbnailGenerator(appContext), thumbnailDispatcher, thumbnailFailures))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(appContext, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(appContext.cacheDir.resolve("thumbnails").toOkioPath())
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(120)
        .build()
}
