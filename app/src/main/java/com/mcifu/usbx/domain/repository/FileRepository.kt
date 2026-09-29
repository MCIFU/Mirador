package com.mcifu.usbx.domain.repository

import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FolderLocation

/** Lectura del contenido de un almacenamiento autorizado. */
interface FileRepository {

    /**
     * Lista una carpeta. Usa la caché en memoria salvo que [forceRefresh] sea `true`.
     * @throws com.mcifu.usbx.domain.model.StorageException
     */
    suspend fun listFolder(location: FolderLocation, forceRefresh: Boolean = false): List<FileItem>

    /** Contenido en caché (sin E/S) o `null`. */
    fun cachedFolder(location: FolderLocation): List<FileItem>?

    /** Descarta la caché de un almacenamiento (desconexión, reconexión, cambios). */
    fun invalidate(storageId: String)
}
