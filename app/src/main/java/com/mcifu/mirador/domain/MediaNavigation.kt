package com.mcifu.mirador.domain

import com.mcifu.mirador.domain.model.FileItem
import com.mcifu.mirador.domain.model.FileType
import com.mcifu.mirador.domain.model.FileTypeRules
import com.mcifu.mirador.domain.model.ViewerScope

/**
 * Construye la secuencia del visor a partir de una carpeta ya ordenada, manteniendo el mismo
 * orden que ve el usuario en el explorador.
 */
object MediaNavigation {

    /**
     * @param alwaysIncludeDocumentId el elemento que el usuario abrió entra siempre, aunque el
     * ámbito elegido no lo incluya (p. ej. abrir una foto con "Solo vídeos").
     */
    fun playlist(
        sortedItems: List<FileItem>,
        scope: ViewerScope,
        sdkInt: Int,
        alwaysIncludeDocumentId: String? = null,
    ): List<FileItem> =
        sortedItems.filter { item ->
            !item.isDirectory && (item.documentId == alwaysIncludeDocumentId || matches(item, scope, sdkInt))
        }

    fun matches(item: FileItem, scope: ViewerScope, sdkInt: Int): Boolean {
        if (item.isDirectory) return false
        val viewableImage = isViewableImage(item, sdkInt)
        return when (scope) {
            ViewerScope.IMAGES -> viewableImage
            ViewerScope.VIDEOS -> item.type == FileType.VIDEO
            ViewerScope.VISUAL_MEDIA -> viewableImage || item.type == FileType.VIDEO
            ViewerScope.ALL_MEDIA -> viewableImage || item.type == FileType.VIDEO || item.type == FileType.AUDIO
            ViewerScope.ALL_FILES -> true
        }
    }

    fun isViewableImage(item: FileItem, sdkInt: Int): Boolean =
        item.type == FileType.IMAGE && FileTypeRules.isInternallyViewableImage(item.name, item.mimeType, sdkInt)

    /** Índice inicial; si el elemento ya no está (borrado), el primero. */
    fun startIndex(playlist: List<FileItem>, documentId: String): Int =
        playlist.indexOfFirst { it.documentId == documentId }.coerceAtLeast(0)
}
