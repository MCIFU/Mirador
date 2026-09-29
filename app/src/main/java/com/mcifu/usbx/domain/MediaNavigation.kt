package com.mcifu.usbx.domain

import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileType
import com.mcifu.usbx.domain.model.FileTypeRules

/** Qué elementos recorre el visor al deslizar. */
enum class ViewerScope {
    /** Solo imágenes que el visor interno sabe mostrar. */
    IMAGES,
    /** Solo vídeos. */
    VIDEOS,
    /** Imágenes y vídeos. */
    VISUAL_MEDIA,
    /** Imágenes, vídeos y audio. */
    ALL_MEDIA,
}

/**
 * Construye la secuencia del visor a partir de una carpeta ya ordenada, manteniendo el mismo
 * orden que ve el usuario en el explorador.
 *
 * Fase 1: el visor interno solo muestra imágenes ([ViewerScope.IMAGES]). El resto de ámbitos
 * se activarán cuando exista el reproductor integrado (Fase 3).
 */
object MediaNavigation {

    fun playlist(sortedItems: List<FileItem>, scope: ViewerScope, sdkInt: Int): List<FileItem> =
        sortedItems.filter { item ->
            val viewableImage = item.type == FileType.IMAGE &&
                FileTypeRules.isInternallyViewableImage(item.name, item.mimeType, sdkInt)
            when (scope) {
                ViewerScope.IMAGES -> viewableImage
                ViewerScope.VIDEOS -> item.type == FileType.VIDEO
                ViewerScope.VISUAL_MEDIA -> viewableImage || item.type == FileType.VIDEO
                ViewerScope.ALL_MEDIA -> viewableImage || item.type == FileType.VIDEO || item.type == FileType.AUDIO
            }
        }

    /** Índice inicial; si el elemento ya no está (borrado), el más cercano posible. */
    fun startIndex(playlist: List<FileItem>, documentId: String): Int =
        playlist.indexOfFirst { it.documentId == documentId }.coerceAtLeast(0)
}
