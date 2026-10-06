package com.mcifu.mirador.domain

/** Motivos por los que un nombre de archivo o carpeta no es válido. */
enum class NameError { EMPTY, INVALID_CHARS, RESERVED, TOO_LONG, TRAILING_DOT_OR_SPACE, ALREADY_EXISTS }

/**
 * Reglas de nombres compatibles con FAT32 y exFAT (los formatos de las memorias USB y tarjetas SD):
 * sin `\ / : * ? " < > |` ni caracteres de control, sin terminar en punto o espacio y como máximo
 * 255 caracteres.
 */
object FileNameRules {
    private val invalidChars = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    const val MAX_LENGTH = 255

    fun validate(name: String, existingNames: Collection<String> = emptyList(), currentName: String? = null): NameError? {
        if (name.isBlank()) return NameError.EMPTY
        if (name == "." || name == "..") return NameError.RESERVED
        if (name.any { it in invalidChars || it.code < 32 }) return NameError.INVALID_CHARS
        if (name.length > MAX_LENGTH) return NameError.TOO_LONG
        if (name.endsWith(".") || name.endsWith(" ")) return NameError.TRAILING_DOT_OR_SPACE
        // FAT y exFAT no distinguen mayúsculas: "Foto.jpg" y "foto.jpg" son el mismo archivo.
        val clash = existingNames.any { it.equals(name, ignoreCase = true) && !it.equals(currentName, ignoreCase = true) }
        if (clash) return NameError.ALREADY_EXISTS
        return null
    }

    fun message(error: NameError): String = when (error) {
        NameError.EMPTY -> "Escribe un nombre"
        NameError.INVALID_CHARS -> "No puede contener \\ / : * ? \" < > |"
        NameError.RESERVED -> "Ese nombre está reservado"
        NameError.TOO_LONG -> "Máximo $MAX_LENGTH caracteres"
        NameError.TRAILING_DOT_OR_SPACE -> "No puede terminar en punto ni en espacio"
        NameError.ALREADY_EXISTS -> "Ya existe un elemento con ese nombre"
    }

    /**
     * Parte del nombre que se selecciona al renombrar (sin la extensión), para que al escribir no
     * se borre ".jpg" por accidente. Devuelve el final exclusivo de la selección.
     */
    fun baseNameEnd(name: String, isDirectory: Boolean): Int {
        if (isDirectory) return name.length
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name.length else dot
    }
}

/** Reglas de copiar/mover. */
object FileOperationRules {

    /** ¿Es [targetFolderId] la propia carpeta [sourceId] o una subcarpeta suya? */
    fun isSelfOrDescendant(sourceId: String, targetFolderId: String): Boolean =
        targetFolderId == sourceId || targetFolderId.startsWith("$sourceId/")

    /** Fracción completada (0..1), por bytes si se conocen y si no por elementos. */
    fun fraction(doneBytes: Long, totalBytes: Long, doneItems: Int, totalItems: Int): Float = when {
        totalBytes > 0 -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        totalItems > 0 -> (doneItems.toFloat() / totalItems).coerceIn(0f, 1f)
        else -> 0f
    }

    /** ¿Cabe la copia? `null` = espacio libre desconocido (se intenta igualmente). */
    fun fits(totalBytes: Long, freeBytes: Long?): Boolean = freeBytes == null || totalBytes <= freeBytes

    /** Límite de tamaño de archivo de FAT32 (4 GiB − 1 byte). */
    const val FAT32_MAX_FILE = 4L * 1024 * 1024 * 1024 - 1
}
