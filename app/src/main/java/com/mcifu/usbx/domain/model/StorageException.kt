package com.mcifu.usbx.domain.model

import java.io.IOException

/** Error de acceso al almacenamiento traducido a causas que la interfaz sabe explicar. */
class StorageException(
    val reason: Reason,
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Reason {
        /** La memoria USB ya no está montada. */
        DISCONNECTED,
        /** El permiso del árbol se revocó o nunca se concedió. */
        PERMISSION_DENIED,
        /** El archivo o la carpeta ya no existe. */
        NOT_FOUND,
        /** Cualquier otro fallo de lectura (sistema de archivos dañado, E/S, etc.). */
        READ_ERROR,
    }
}
