package com.mcifu.mirador.domain.model

enum class FileType {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    APK,
    OTHER;

    /** Imágenes y vídeos: tienen miniatura visual y se recorren en los visores. */
    val isVisualMedia: Boolean get() = this == IMAGE || this == VIDEO

    val isMedia: Boolean get() = this == IMAGE || this == VIDEO || this == AUDIO
}
