package com.mcifu.mirador.data.external

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.mcifu.mirador.data.diagnostics.Diagnostics
import com.mcifu.mirador.data.storage.AndroidStorageRepository
import com.mcifu.mirador.domain.ExternalPaths
import com.mcifu.mirador.domain.VolumeDocument
import com.mcifu.mirador.domain.model.FileTypeRules
import com.mcifu.mirador.domain.model.FolderLocation
import com.mcifu.mirador.domain.model.OpenTarget
import com.mcifu.mirador.domain.repository.FileRepository
import com.mcifu.mirador.domain.repository.StorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Convierte un archivo abierto desde otra app ("Abrir con Mirador" desde Mis archivos, la galería,
 * WhatsApp…) en lo que debe mostrar el visor.
 *
 * - Si el archivo está en una memoria a la que Mirador ya tiene acceso, se abre con su carpeta:
 *   se puede deslizar entre fotos y vídeos igual que desde el explorador.
 * - Si no, se abre solo ese archivo (con zoom, reproductor y todas las funciones).
 */
class ExternalOpenResolver(
    private val context: Context,
    private val storageRepository: StorageRepository,
    private val fileRepository: FileRepository,
) {

    suspend fun resolve(uri: Uri, intentMime: String?): OpenTarget = withContext(Dispatchers.IO) {
        val meta = queryMeta(uri)
        val name = meta.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Archivo"
        val reported = intentMime?.takeIf { it != "*/*" } ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val mime = FileTypeRules.effectiveMime(name, reported, isDirectory = false)

        val documentCandidate = runCatching {
            if (uri.authority == AndroidStorageRepository.EXTERNAL_STORAGE_AUTHORITY && DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                ExternalPaths.parentOf(docId)?.let { parent -> VolumeDocument(docId.substringBefore(':'), docId, parent) }
            } else {
                null
            }
        }.getOrNull()
        val candidate = documentCandidate ?: ExternalPaths.volumeDocumentFor(meta.path)

        val folderRoute = candidate?.let { folderRouteFor(it) }
        Diagnostics.log(
            "EXTERNO",
            "Recibido $name ($mime) de ${uri.authority}; ruta=${meta.path ?: "desconocida"} → " +
                if (folderRoute != null) "visor con carpeta" else "visor de un solo archivo",
        )
        folderRoute ?: OpenTarget.SingleFile(uri = uri.toString(), mimeType = mime, name = name, size = meta.size ?: 0L)
    }

    private suspend fun folderRouteFor(candidate: VolumeDocument): OpenTarget.InFolder? {
        val storage = storageRepository.storages.value.firstOrNull { it.id == candidate.volumeId && it.canBrowse } ?: return null
        val access = storage.access ?: return null
        if (!ExternalPaths.isInsideTree(candidate.parentDocumentId, access.rootDocumentId) &&
            candidate.parentDocumentId != access.rootDocumentId
        ) return null
        val location = FolderLocation(storage.id, access.treeUri, candidate.parentDocumentId)
        val items = runCatching { fileRepository.listFolder(location) }.getOrNull() ?: return null
        if (items.none { it.documentId == candidate.documentId }) return null
        val relativeParent = candidate.parentDocumentId.substringAfter(':')
        val path = listOf(storage.label) + relativeParent.split('/').filter { it.isNotEmpty() }
        return OpenTarget.InFolder(
            storageId = storage.id,
            treeUri = access.treeUri.toString(),
            folderDocumentId = candidate.parentDocumentId,
            documentId = candidate.documentId,
            path = path,
        )
    }

    private data class Meta(val name: String?, val size: Long?, val path: String?)

    private fun queryMeta(uri: Uri): Meta {
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        }
        // La ruta real no forma parte del estándar, pero muchas apps (y MediaStore) la exponen en
        // la columna "_data". Se pide aparte porque algunos proveedores fallan si no la tienen.
        val path = runCatching {
            resolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull()
        return Meta(name, size, path)
    }

}
