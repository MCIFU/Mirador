package com.mcifu.mirador.data.operations

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.data.storage.contentUri
import com.mcifu.mirador.domain.FileOperationRules
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.FolderLocation
import com.mcifu.mirador.domain.model.OperationKind
import com.mcifu.mirador.domain.model.OperationProgress
import com.mcifu.mirador.domain.model.OperationStatus
import com.mcifu.mirador.domain.model.StorageException
import com.mcifu.mirador.domain.repository.FileOperations
import com.mcifu.mirador.domain.repository.FileRepository
import com.mcifu.mirador.domain.repository.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Copiar, mover, eliminar, renombrar y crear carpetas con el Storage Access Framework.
 *
 * Seguridad de los datos:
 * - Copiar crea archivos nuevos; si falla o se cancela, se borra el archivo a medio copiar.
 * - Mover dentro de la misma memoria usa `moveDocument` (instantáneo, sin copiar datos). Entre
 *   memorias se copia y el original solo se borra cuando su copia ha terminado bien.
 * - No se puede copiar ni mover una carpeta dentro de sí misma.
 * - Antes de copiar se comprueba el espacio libre del destino (si Android lo informa).
 *
 * Las operaciones largas viven en el ámbito de la aplicación: siguen aunque cambies de pantalla.
 */
class SafFileOperations(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val fileRepository: FileRepository,
    private val storageRepository: StorageRepository,
) : FileOperations {

    private val resolver get() = context.contentResolver
    private val _progress = MutableStateFlow<OperationProgress?>(null)
    override val progress: StateFlow<OperationProgress?> = _progress.asStateFlow()
    private val _changes = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)
    override val changes: SharedFlow<Set<String>> = _changes.asSharedFlow()
    private var job: Job? = null
    private var lastPublish = 0L

    // --- Operaciones rápidas -----------------------------------------------------------------

    override suspend fun createFolder(parent: FolderLocation, name: String) = withContext(Dispatchers.IO) {
        guard("crear la carpeta") {
            DocumentsContract.createDocument(resolver, parentUri(parent), DocumentsContract.Document.MIME_TYPE_DIR, name)
                ?: throw IOException("El proveedor no creó la carpeta")
        }
        Diagnostics.log("ARCHIVOS", "Carpeta creada: $name")
        finishChange(setOf(parent.storageId))
    }

    override suspend fun rename(item: FileItem, parent: FolderLocation, newName: String) = withContext(Dispatchers.IO) {
        guard("renombrar") {
            DocumentsContract.renameDocument(resolver, item.contentUri, newName)
                ?: throw IOException("El proveedor no renombró el elemento")
        }
        Diagnostics.log("ARCHIVOS", "Renombrado: ${item.name} → $newName")
        finishChange(setOf(parent.storageId))
    }

    // --- Operaciones largas -------------------------------------------------------------------

    override fun copy(items: List<FileItem>, source: FolderLocation, target: FolderLocation) =
        launchOperation(OperationKind.COPY, items) { transfer(items, source, target, move = false) }

    override fun move(items: List<FileItem>, source: FolderLocation, target: FolderLocation) =
        launchOperation(OperationKind.MOVE, items) { transfer(items, source, target, move = true) }

    override fun delete(items: List<FileItem>, source: FolderLocation) =
        launchOperation(OperationKind.DELETE, items) {
            _progress.update { it?.copy(status = OperationStatus.RUNNING, totalItems = items.size) }
            val failed = mutableListOf<String>()
            items.forEachIndexed { index, item ->
                currentCoroutineContext().ensureActive()
                publish(force = true) { it.copy(currentName = item.name, doneItems = index) }
                try {
                    if (!DocumentsContract.deleteDocument(resolver, item.contentUri)) failed += item.name
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Diagnostics.log("ARCHIVOS", "No se pudo eliminar ${item.name}", e)
                    failed += item.name
                }
            }
            Diagnostics.log("ARCHIVOS", "Eliminados ${items.size - failed.size} de ${items.size}")
            Outcome(setOf(source.storageId), failed)
        }

    override fun cancel() {
        job?.cancel()
    }

    override fun dismiss() {
        if (_progress.value?.isFinished == true) _progress.value = null
    }

    private data class Outcome(val changedStorages: Set<String>, val failed: List<String>)

    private fun launchOperation(kind: OperationKind, items: List<FileItem>, block: suspend () -> Outcome): Boolean {
        if (job?.isActive == true || items.isEmpty()) return false
        _progress.value = OperationProgress(kind = kind, totalItems = items.size)
        job = appScope.launch(Dispatchers.IO) {
            try {
                val outcome = block()
                _progress.update {
                    it?.copy(
                        status = if (outcome.failed.isEmpty()) OperationStatus.DONE else OperationStatus.FAILED,
                        doneItems = it.totalItems - outcome.failed.size,
                        doneBytes = it.totalBytes.takeIf { outcome.failed.isEmpty() } ?: it.doneBytes,
                        failedNames = outcome.failed,
                        currentName = null,
                        message = if (outcome.failed.isEmpty()) null else "No se pudieron procesar ${outcome.failed.size} elementos",
                    )
                }
                finishChange(outcome.changedStorages)
            } catch (e: CancellationException) {
                _progress.update { it?.copy(status = OperationStatus.CANCELLED, currentName = null) }
                finishChange(storageRepository.storages.value.map { it.id }.toSet())
            } catch (e: Exception) {
                Diagnostics.log("ARCHIVOS", "Operación $kind fallida", e)
                _progress.update {
                    it?.copy(status = OperationStatus.FAILED, currentName = null, message = (e as? StorageException)?.message ?: friendly(e))
                }
                finishChange(storageRepository.storages.value.map { it.id }.toSet())
            }
        }
        return true
    }

    // --- Copiar / mover -----------------------------------------------------------------------

    private suspend fun transfer(items: List<FileItem>, source: FolderLocation, target: FolderLocation, move: Boolean): Outcome {
        if (move && source.documentId == target.documentId && source.storageId == target.storageId) {
            throw StorageException(StorageException.Reason.READ_ERROR, "Ya está en esa carpeta")
        }
        items.filter { it.isDirectory && source.storageId == target.storageId }.forEach { folder ->
            if (FileOperationRules.isSelfOrDescendant(folder.documentId, target.documentId)) {
                throw StorageException(StorageException.Reason.READ_ERROR, "No se puede ${if (move) "mover" else "copiar"} «${folder.name}» dentro de sí misma")
            }
        }

        val sameStorage = source.storageId == target.storageId && source.treeUri == target.treeUri
        val needsCopy = !move || !sameStorage
        // Recorrido previo: tamaño total para la barra de progreso y la comprobación de espacio.
        val totalBytes = if (needsCopy) items.sumOf { sizeOf(it, source) } else 0L
        val free = storageRepository.storages.value.firstOrNull { it.id == target.storageId }?.freeBytes
        if (needsCopy && !FileOperationRules.fits(totalBytes, free)) {
            throw StorageException(StorageException.Reason.READ_ERROR, "No hay espacio suficiente en el destino")
        }
        _progress.update { it?.copy(status = OperationStatus.RUNNING, totalBytes = totalBytes) }

        val failed = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            publish(force = true) { it.copy(currentName = item.name, doneItems = index) }
            try {
                if (move && sameStorage && tryNativeMove(item, source, target)) return@forEachIndexed
                copyRecursive(item, source, target.treeUri, target.documentId, target.storageId)
                // El original solo se borra cuando la copia ha terminado sin errores.
                if (move) DocumentsContract.deleteDocument(resolver, item.contentUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.log("ARCHIVOS", "Fallo al ${if (move) "mover" else "copiar"} ${item.name}", e)
                failed += item.name
            }
        }
        Diagnostics.log("ARCHIVOS", "${if (move) "Movidos" else "Copiados"} ${items.size - failed.size} de ${items.size}")
        return Outcome(setOf(source.storageId, target.storageId), failed)
    }

    private fun tryNativeMove(item: FileItem, source: FolderLocation, target: FolderLocation): Boolean = try {
        DocumentsContract.moveDocument(resolver, item.contentUri, parentUri(source), parentUri(target)) != null
    } catch (e: Exception) {
        // Por ejemplo, ya existe un archivo con ese nombre: se cae a copiar + borrar (que renombra).
        Diagnostics.log("ARCHIVOS", "Movimiento directo no disponible para ${item.name}, se copia", e)
        false
    }

    private suspend fun copyRecursive(item: FileItem, source: FolderLocation, targetTree: Uri, targetParentId: String, targetStorageId: String) {
        val targetParent = DocumentsContract.buildDocumentUriUsingTree(targetTree, targetParentId)
        if (item.isDirectory) {
            val newDir = DocumentsContract.createDocument(resolver, targetParent, DocumentsContract.Document.MIME_TYPE_DIR, item.name)
                ?: throw IOException("No se pudo crear la carpeta ${item.name}")
            val newDirId = DocumentsContract.getDocumentId(newDir)
            val children = fileRepository.listFolder(FolderLocation(source.storageId, source.treeUri, item.documentId), forceRefresh = true)
            for (child in children) {
                currentCoroutineContext().ensureActive()
                copyRecursive(child, FolderLocation(source.storageId, source.treeUri, item.documentId), targetTree, newDirId, targetStorageId)
            }
            return
        }
        // "application/octet-stream" hace que el proveedor conserve el nombre exacto (con su extensión).
        val newFile = DocumentsContract.createDocument(resolver, targetParent, "application/octet-stream", item.name)
            ?: throw IOException("No se pudo crear ${item.name}")
        try {
            _progress.update { it?.copy(currentName = item.name) }
            val input = resolver.openInputStream(item.contentUri) ?: throw FileNotFoundException(item.name)
            val output = resolver.openOutputStream(newFile, "w") ?: throw FileNotFoundException("destino de ${item.name}")
            input.use { inp ->
                output.use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var pending = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = inp.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        pending += read
                        if (shouldPublish()) {
                            val add = pending
                            pending = 0
                            _progress.update { it?.copy(doneBytes = it.doneBytes + add) }
                        }
                    }
                    val add = pending
                    _progress.update { it?.copy(doneBytes = it.doneBytes + add) }
                }
            }
        } catch (e: Throwable) {
            // Nunca se deja un archivo a medias en el destino.
            runCatching { DocumentsContract.deleteDocument(resolver, newFile) }
            if (e is IOException && item.size > FileOperationRules.FAT32_MAX_FILE) {
                throw IOException("«${item.name}» ocupa más de 4 GB: el destino (FAT32) no admite archivos tan grandes", e)
            }
            throw e
        }
    }

    private suspend fun sizeOf(item: FileItem, parent: FolderLocation): Long {
        if (!item.isDirectory) return item.size
        val children = runCatching {
            fileRepository.listFolder(FolderLocation(parent.storageId, parent.treeUri, item.documentId), forceRefresh = true)
        }.getOrDefault(emptyList())
        return children.sumOf { sizeOf(it, FolderLocation(parent.storageId, parent.treeUri, item.documentId)) }
    }

    // --- Utilidades ---------------------------------------------------------------------------

    private fun parentUri(location: FolderLocation): Uri =
        DocumentsContract.buildDocumentUriUsingTree(location.treeUri, location.documentId)

    private fun publish(force: Boolean = false, transform: (OperationProgress) -> OperationProgress) {
        if (force || shouldPublish()) _progress.update { it?.let(transform) }
    }

    /** Como mucho una actualización cada 100 ms: copiar genera miles de bloques por segundo. */
    private fun shouldPublish(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPublish < 100) return false
        lastPublish = now
        return true
    }

    private suspend fun finishChange(storageIds: Set<String>) {
        storageIds.forEach { fileRepository.invalidate(it) }
        storageRepository.refresh() // el espacio libre ha cambiado
        _changes.emit(storageIds)
    }

    private inline fun guard(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.log("ARCHIVOS", "No se pudo $action", e)
            throw StorageException(StorageException.Reason.READ_ERROR, "No se pudo $action: ${friendly(e)}", e)
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is SecurityException -> "sin permiso de escritura en esta memoria"
        is FileNotFoundException -> "el archivo ya no existe"
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
