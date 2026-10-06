package com.mcifu.mirador.domain

/** Documento de un volumen extraíble deducido de una ruta de archivo. */
data class VolumeDocument(val volumeId: String, val documentId: String, val parentDocumentId: String)

/**
 * Traduce rutas como `/storage/1A2B-3C4D/DCIM/Camera/IMG.jpg` (las que exponen muchas apps de
 * archivos y la galería) al documentId del proveedor de almacenamiento externo
 * (`1A2B-3C4D:DCIM/Camera/IMG.jpg`). Así un archivo abierto desde otra app se puede mostrar con
 * su carpeta, siempre que Mirador tenga acceso a ese volumen.
 */
object ExternalPaths {

    private val volumeRoots = listOf("/storage/", "/mnt/media_rw/")

    fun volumeDocumentFor(path: String?): VolumeDocument? {
        if (path.isNullOrBlank()) return null
        val root = volumeRoots.firstOrNull { path.startsWith(it) } ?: return null
        val rest = path.removePrefix(root).trimEnd('/')
        val volumeId = rest.substringBefore('/', missingDelimiterValue = "")
        val relative = rest.substringAfter('/', missingDelimiterValue = "")
        if (volumeId.isEmpty() || relative.isEmpty()) return null
        // Almacenamiento interno: no es un volumen extraíble.
        if (volumeId == "emulated" || volumeId == "self") return null
        val parentRelative = relative.substringBeforeLast('/', missingDelimiterValue = "")
        return VolumeDocument(
            volumeId = volumeId,
            documentId = "$volumeId:$relative",
            parentDocumentId = "$volumeId:$parentRelative",
        )
    }

    /** "1A2B-3C4D:DCIM/Camera/IMG.jpg" → documento padre "1A2B-3C4D:DCIM/Camera". */
    fun parentOf(documentId: String): String? {
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        if (volume.isEmpty()) return null
        val relative = documentId.substringAfter(':')
        if (relative.isEmpty()) return null
        return "$volume:${relative.substringBeforeLast('/', missingDelimiterValue = "")}"
    }

    /** ¿Está el documento dentro del árbol autorizado (raíz o subcarpeta)? */
    fun isInsideTree(documentId: String, treeRootDocumentId: String): Boolean {
        if (treeRootDocumentId.endsWith(":")) return documentId.startsWith(treeRootDocumentId)
        return documentId == treeRootDocumentId || documentId.startsWith("$treeRootDocumentId/")
    }
}
