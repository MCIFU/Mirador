package com.mcifu.usbx.ui.navigation

import com.mcifu.usbx.domain.model.OpenTarget
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

/**
 * Una carpeta del explorador. Cada carpeta es una entrada de la pila de navegación, así el
 * botón Atrás sube de nivel y cada carpeta conserva su posición de scroll.
 *
 * @param path nombres desde la raíz del almacenamiento hasta esta carpeta (para la ruta visible).
 */
@Serializable
data class BrowserRoute(
    val storageId: String,
    val treeUri: String,
    val documentId: String,
    val path: List<String>,
)

/**
 * Visor a pantalla completa. Recibe la carpeta y el elemento inicial; la lista se obtiene de la
 * caché del repositorio (sin volver a leer el USB) y se filtra según el ámbito elegido.
 */
@Serializable
data class ViewerRoute(
    val storageId: String,
    val treeUri: String,
    val folderDocumentId: String,
    val startDocumentId: String,
    val path: List<String>,
    /** Archivo abierto desde otra app sin carpeta accesible: el visor muestra solo este archivo. */
    val externalUri: String? = null,
    val externalMime: String? = null,
    val externalName: String? = null,
    val externalSize: Long = 0,
)

/** Ruta del visor para un archivo abierto desde otra app. */
fun OpenTarget.toViewerRoute(): ViewerRoute = when (this) {
    is OpenTarget.InFolder -> ViewerRoute(storageId, treeUri, folderDocumentId, documentId, path)
    is OpenTarget.SingleFile -> ViewerRoute(
        storageId = "external-file",
        treeUri = "",
        folderDocumentId = "",
        startDocumentId = uri,
        path = listOf(name),
        externalUri = uri,
        externalMime = mimeType,
        externalName = name,
        externalSize = size,
    )
}

/** Multiview: dos fotos o vídeos de la misma carpeta a la vez. */
@Serializable
data class MultiviewRoute(
    val storageId: String,
    val treeUri: String,
    val folderDocumentId: String,
    val firstDocumentId: String,
    val path: List<String>,
)
