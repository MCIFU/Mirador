package com.mcifu.mirador.domain.model

/** Qué mostrar cuando otra app pide abrir un archivo con Mirador. */
sealed interface OpenTarget {
    /** El archivo está en una memoria autorizada: se abre con su carpeta. */
    data class InFolder(
        val storageId: String,
        val treeUri: String,
        val folderDocumentId: String,
        val documentId: String,
        val path: List<String>,
    ) : OpenTarget

    /** Solo ese archivo (sin acceso a su carpeta). */
    data class SingleFile(
        val uri: String,
        val mimeType: String,
        val name: String,
        val size: Long,
    ) : OpenTarget
}
