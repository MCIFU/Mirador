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
