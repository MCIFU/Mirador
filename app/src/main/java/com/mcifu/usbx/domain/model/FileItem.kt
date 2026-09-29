package com.mcifu.usbx.domain.model

import android.net.Uri

/**
 * Entrada de una carpeta de la memoria USB.
 *
 * [uri] es el URI de documento (content://…) derivado del árbol autorizado, guardado como texto
 * para que el dominio no dependa de clases de Android y se pueda probar en la JVM.
 * Las capas de datos e interfaz lo convierten con `contentUri`.
 */
data class FileItem(
    val documentId: String,
    val uri: String,
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
