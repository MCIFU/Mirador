package com.mcifu.mirador.domain.model

import android.net.Uri

/** Tipo de ubicación que aparece en la pantalla principal. */
enum class StorageKind { USB, SD_CARD, FOLDER }

/** Estado de montaje del volumen tal y como lo informa Android. */
enum class MountState { MOUNTED, READ_ONLY, CHECKING, UNAVAILABLE }

/**
 * Acceso concedido por el usuario mediante el Storage Access Framework.
 * [treeUri] es el URI persistente del árbol; [rootDocumentId] el documento raíz de ese árbol.
 */
data class StorageAccess(
    val treeUri: Uri,
    val rootDocumentId: String,
)

/**
 * Memoria USB (o tarjeta SD / carpeta autorizada manualmente).
 *
 * [id] es el UUID del volumen cuando Android lo expone (p. ej. "1A2B-3C4D"); para carpetas
 * autorizadas manualmente fuera de un volumen extraíble es el propio URI del árbol.
 */
data class UsbStorage(
    val id: String,
    val label: String,
    val kind: StorageKind,
    val mountState: MountState,
    val access: StorageAccess?,
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
) {
    val isAvailable: Boolean
        get() = mountState == MountState.MOUNTED || mountState == MountState.READ_ONLY

    val isAuthorized: Boolean get() = access != null

    val canBrowse: Boolean get() = isAvailable && isAuthorized
}
