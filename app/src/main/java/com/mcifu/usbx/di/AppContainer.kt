package com.mcifu.usbx.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.mcifu.usbx.data.settings.DataStoreSettingsRepository
import com.mcifu.usbx.data.storage.AndroidFileDetailsRepository
import com.mcifu.usbx.data.storage.AndroidStorageRepository
import com.mcifu.usbx.data.storage.SafFileRepository
import com.mcifu.usbx.data.thumbnail.ThumbnailFetcher
import com.mcifu.usbx.data.thumbnail.ThumbnailGenerator
import com.mcifu.usbx.data.thumbnail.ThumbnailKeyer
import com.mcifu.usbx.domain.repository.FileDetailsRepository
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.SettingsRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath

/**
 * Inyección de dependencias manual. El grafo es pequeño y no justifica Hilt/Dagger:
 * menos tiempo de compilación y ninguna magia.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val storageRepository: StorageRepository = AndroidStorageRepository(appContext, appScope)

    val fileRepository: FileRepository = SafFileRepository(appContext)

    val fileDetailsRepository: FileDetailsRepository = AndroidFileDetailsRepository(appContext)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext, appScope)

    /**
     * Generación de miniaturas limitada a 3 en paralelo: una memoria USB atiende las lecturas
     * de una en una por el bus, así que más paralelismo solo añade contención y retrasa las
     * miniaturas visibles.
     */
    private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(3)

    fun createImageLoader(): ImageLoader = ImageLoader.Builder(appContext)
        .components {
            add(ThumbnailKeyer())
            add(ThumbnailFetcher.Factory(ThumbnailGenerator(appContext), thumbnailDispatcher))
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
