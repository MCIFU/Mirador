package com.mcifu.mirador.domain.model

/** Información ampliada de un archivo. Los campos opcionales dependen del tipo y del formato. */
data class FileDetails(
    val item: FileItem,
    val storageLabel: String,
    val path: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    /** Metadatos adicionales ya legibles (p. ej. "Cámara" → "samsung SM-M526B"). */
    val extra: List<Pair<String, String>> = emptyList(),
)
