package com.mcifu.usbx.domain.model

/** Qué elementos recorre el visor al deslizar. */
enum class ViewerScope {
    /** Solo imágenes que el visor interno sabe mostrar. */
    IMAGES,
    /** Fotos y vídeos (predeterminado: lo habitual en una memoria con fotos de cámara). */
    VISUAL_MEDIA,
    /** Solo vídeos. */
    VIDEOS,
    /** Imágenes, vídeos y audio. */
    ALL_MEDIA,
    /** Cualquier archivo (los no multimedia se muestran como ficha con "Abrir con…"). */
    ALL_FILES,
}

/** Orientación de pantalla en el visor (y, en la Fase 3, en el reproductor). */
enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

data class ViewerSettings(
    val scope: ViewerScope = ViewerScope.VISUAL_MEDIA,
    val orientation: OrientationMode = OrientationMode.AUTO,
    val showFilmstrip: Boolean = true,
)
