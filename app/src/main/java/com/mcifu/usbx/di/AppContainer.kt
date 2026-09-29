package com.mcifu.usbx.di

import android.content.Context
import com.mcifu.usbx.data.storage.AndroidStorageRepository
import com.mcifu.usbx.data.storage.SafFileRepository
import com.mcifu.usbx.domain.repository.FileRepository
import com.mcifu.usbx.domain.repository.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Inyección de dependencias manual. El grafo es pequeño y no justifica Hilt/Dagger:
 * menos tiempo de compilación y ninguna magia.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val storageRepository: StorageRepository = AndroidStorageRepository(appContext, appScope)

    val fileRepository: FileRepository = SafFileRepository(appContext)
}
