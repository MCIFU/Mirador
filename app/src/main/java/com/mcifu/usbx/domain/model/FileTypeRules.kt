package com.mcifu.usbx.domain.model

/**
 * Reglas puras (sin dependencias de Android) para clasificar archivos.
 * Se usa la extensión como respaldo porque algunos proveedores devuelven
 * "application/octet-stream" para formatos que Android no conoce.
 */
object FileTypeRules {

    const val MIME_DIRECTORY = "vnd.android.document/directory"
    const val MIME_UNKNOWN = "application/octet-stream"

    private val imageExt = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "jpe" to "image/jpeg",
        "png" to "image/png", "webp" to "image/webp", "gif" to "image/gif",
        "bmp" to "image/bmp", "heic" to "image/heic", "heif" to "image/heif",
        "avif" to "image/avif", "tif" to "image/tiff", "tiff" to "image/tiff",
        "svg" to "image/svg+xml", "dng" to "image/x-adobe-dng",
    )
    private val videoExt = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska",
        "mov" to "video/quicktime", "avi" to "video/x-msvideo", "webm" to "video/webm",
        "3gp" to "video/3gpp", "3g2" to "video/3gpp2", "ts" to "video/mp2ts",
        "mts" to "video/mp2ts", "m2ts" to "video/mp2ts", "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv", "mpg" to "video/mpeg", "mpeg" to "video/mpeg",
    )
    private val audioExt = mapOf(
        "mp3" to "audio/mpeg", "wav" to "audio/x-wav", "flac" to "audio/flac",
        "m4a" to "audio/mp4", "ogg" to "audio/ogg", "oga" to "audio/ogg",
        "opus" to "audio/opus", "aac" to "audio/aac", "wma" to "audio/x-ms-wma",
        "amr" to "audio/amr", "mid" to "audio/midi", "midi" to "audio/midi",
    )
    private val documentExt = mapOf(
        "pdf" to "application/pdf", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "txt" to "text/plain", "rtf" to "application/rtf", "csv" to "text/csv",
        "md" to "text/markdown", "epub" to "application/epub+zip",
        "html" to "text/html", "htm" to "text/html", "xml" to "text/xml",
        "json" to "application/json", "log" to "text/plain", "srt" to "application/x-subrip",
    )
    private val archiveExt = mapOf(
        "zip" to "application/zip", "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed", "tar" to "application/x-tar",
        "gz" to "application/gzip", "bz2" to "application/x-bzip2", "xz" to "application/x-xz",
    )
    private const val APK_MIME = "application/vnd.android.package-archive"

    /** Nombres de sistema que se ocultan salvo que el usuario active "Mostrar ocultos". */
    private val systemNames = setOf("system volume information", "\$recycle.bin", "lost.dir")

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) "" else name.substring(dot + 1).lowercase()
    }

    fun isHiddenName(name: String): Boolean =
        name.startsWith(".") || name.lowercase() in systemNames

    /** Devuelve el MIME fiable: el del proveedor si es específico, si no el deducido de la extensión. */
    fun effectiveMime(name: String, reportedMime: String?, isDirectory: Boolean): String {
        if (isDirectory) return MIME_DIRECTORY
        val reported = reportedMime?.lowercase()
        if (!reported.isNullOrBlank() && reported != MIME_UNKNOWN && reported != "*/*") return reported
        val ext = extensionOf(name)
        return imageExt[ext] ?: videoExt[ext] ?: audioExt[ext] ?: documentExt[ext]
            ?: archiveExt[ext] ?: (if (ext == "apk") APK_MIME else null) ?: MIME_UNKNOWN
    }

    fun classify(name: String, mimeType: String?, isDirectory: Boolean): FileType {
        if (isDirectory || mimeType == MIME_DIRECTORY) return FileType.FOLDER
        val ext = extensionOf(name)
        // La extensión manda para los casos ambiguos (p. ej. ".ts" puede llegar como texto).
        when (ext) {
            in videoExt -> return FileType.VIDEO
            in imageExt -> return FileType.IMAGE
            in audioExt -> return FileType.AUDIO
            in archiveExt -> return FileType.ARCHIVE
            "apk" -> return FileType.APK
            in documentExt -> return FileType.DOCUMENT
        }
        val mime = mimeType?.lowercase().orEmpty()
        return when {
            mime.startsWith("image/") -> FileType.IMAGE
            mime.startsWith("video/") -> FileType.VIDEO
            mime.startsWith("audio/") -> FileType.AUDIO
            mime == APK_MIME -> FileType.APK
            mime in archiveExt.values -> FileType.ARCHIVE
            mime.startsWith("text/") || mime in documentExt.values -> FileType.DOCUMENT
            else -> FileType.OTHER
        }
    }

    /**
     * ¿Puede el visor interno de USBX mostrar esta imagen?
     * HEIC/HEIF requieren Android 9 (API 28) y AVIF Android 12 (API 31). TIFF, SVG y RAW se
     * delegan a otras apps.
     */
    fun isInternallyViewableImage(name: String, mimeType: String, sdkInt: Int): Boolean {
        return when (extensionOf(name).ifEmpty { mimeType.substringAfter('/') }) {
            "jpg", "jpeg", "jpe", "png", "webp", "gif", "bmp" -> true
            "heic", "heif" -> sdkInt >= 28
            "avif" -> sdkInt >= 31
            else -> mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
        }
    }
}
