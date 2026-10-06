package com.mcifu.mirador.data.storage

import android.content.Context
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.LruCache
import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.FileTypeRules
import com.mcifu.mirador.domain.model.FolderLocation
import com.mcifu.mirador.domain.model.StorageException
import com.mcifu.mirador.domain.repository.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import kotlinx.coroutines.currentCoroutineContext

/**
 * Lectura de carpetas con `DocumentsContract`.
 *
 * Se hace UNA sola consulta al proveedor por carpeta con una proyección mínima. Es mucho más
 * rápido que `DocumentFile.listFiles()`, que lanza una consulta extra por cada archivo para
 * leer nombre, tipo o tamaño: con miles de fotos la diferencia es de segundos.
 */
class SafFileRepository(
    private val context: Context,
) : FileRepository {

    /** Últimas carpetas visitadas: volver atrás o abrir el visor no repite la consulta. */
    private val cache = LruCache<String, List<FileItem>>(48)

    override suspend fun listFolder(location: FolderLocation, forceRefresh: Boolean): List<FileItem> {
        val key = keyOf(location)
        if (!forceRefresh) cache.get(key)?.let { return it }
        val items = withContext(Dispatchers.IO) { query(location) }
        cache.put(key, items)
        return items
    }

    override fun cachedFolder(location: FolderLocation): List<FileItem>? = cache.get(keyOf(location))

    override fun invalidate(storageId: String) {
        val prefix = "$storageId|"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    private suspend fun query(location: FolderLocation): List<FileItem> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(location.treeUri, location.documentId)
        try {
            val cursor = context.contentResolver.query(childrenUri, PROJECTION, null, null, null)
                ?: throw StorageException(StorageException.Reason.READ_ERROR, "El proveedor no devolvió resultados")
            return cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(Document.COLUMN_SIZE)
                val modifiedCol = c.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
                val result = ArrayList<FileItem>(c.count)
                while (c.moveToNext()) {
                    if (result.size % 256 == 0) currentCoroutineContext().ensureActive()
                    val documentId = c.getString(idCol) ?: continue
                    val name = c.getString(nameCol) ?: documentId.substringAfterLast('/')
                    val reportedMime = c.getString(mimeCol)
                    val isDirectory = reportedMime == Document.MIME_TYPE_DIR
                    val mime = FileTypeRules.effectiveMime(name, reportedMime, isDirectory)
                    result += FileItem(
                        documentId = documentId,
                        uri = DocumentsContract.buildDocumentUriUsingTree(location.treeUri, documentId).toString(),
                        name = name,
                        mimeType = mime,
                        type = FileTypeRules.classify(name, mime, isDirectory),
                        size = if (c.isNull(sizeCol)) 0L else c.getLong(sizeCol),
                        lastModified = if (c.isNull(modifiedCol)) 0L else c.getLong(modifiedCol),
                    )
                }
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: SecurityException) {
            throw StorageException(StorageException.Reason.PERMISSION_DENIED, e.message, e)
        } catch (e: FileNotFoundException) {
            throw StorageException(StorageException.Reason.NOT_FOUND, e.message, e)
        } catch (e: IllegalArgumentException) {
            // ExternalStorageProvider lanza IllegalArgumentException si la ruta ya no existe
            // o si el volumen desapareció a mitad de la consulta.
            throw StorageException(StorageException.Reason.NOT_FOUND, e.message, e)
        } catch (e: Exception) {
            throw StorageException(StorageException.Reason.READ_ERROR, e.message, e)
        }
    }

    private fun keyOf(location: FolderLocation) = "${location.storageId}|${location.treeUri}|${location.documentId}"

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
