package com.mcifu.usbx.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

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
)

/** Multiview: dos fotos o vídeos de la misma carpeta a la vez. */
@Serializable
data class MultiviewRoute(
    val storageId: String,
    val treeUri: String,
    val folderDocumentId: String,
    val firstDocumentId: String,
    val path: List<String>,
)
