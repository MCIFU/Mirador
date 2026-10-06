package com.mcifu.mirador.domain.repository

import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.FolderLocation
import com.mcifu.mirador.domain.model.OperationProgress
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Operaciones que modifican la memoria. Las largas (copiar, mover, eliminar) van de una en una. */
interface FileOperations {

    /** Operación larga en curso o recién terminada (`null` = ninguna). */
    val progress: StateFlow<OperationProgress?>

    /** Almacenamientos modificados por cada operación terminada (para refrescar las carpetas). */
    val changes: SharedFlow<Set<String>>

    /** @throws com.mcifu.mirador.domain.model.StorageException */
    suspend fun createFolder(parent: FolderLocation, name: String)

    /** @throws com.mcifu.mirador.domain.model.StorageException */
    suspend fun rename(item: FileItem, parent: FolderLocation, newName: String)

    /** Devuelve `false` si ya hay otra operación en marcha. */
    fun copy(items: List<FileItem>, source: FolderLocation, target: FolderLocation): Boolean
    fun move(items: List<FileItem>, source: FolderLocation, target: FolderLocation): Boolean
    fun delete(items: List<FileItem>, source: FolderLocation): Boolean

    fun cancel()

    /** Oculta el resultado de una operación terminada. */
    fun dismiss()
}
