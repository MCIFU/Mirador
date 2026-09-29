package com.mcifu.usbx.domain.model

import android.net.Uri

/**
 * Entrada de una carpeta de la memoria USB.
 *
 * [uri] es un URI de documento derivado del árbol autorizado; es el que se usa para leer,
 * generar miniaturas, abrir con otras apps o compartir.
 */
data class FileItem(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val type: FileType,
    val size: Long,
    val lastModified: Long,
) {
    val isDirectory: Boolean get() = type == FileType.FOLDER
    val isHidden: Boolean get() = FileTypeRules.isHiddenName(name)
    val extension: String get() = FileTypeRules.extensionOf(name)
}

/** Ubicación de una carpeta concreta dentro de un almacenamiento autorizado. */
data class FolderLocation(
    val storageId: String,
    val treeUri: Uri,
    val documentId: String,
)
