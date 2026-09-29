package com.mcifu.usbx.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mcifu.usbx.domain.model.FileType

fun FileType.icon(): ImageVector = when (this) {
    FileType.FOLDER -> Icons.Outlined.Folder
    FileType.IMAGE -> Icons.Outlined.Image
    FileType.VIDEO -> Icons.Outlined.Movie
    FileType.AUDIO -> Icons.Outlined.Audiotrack
    FileType.DOCUMENT -> Icons.Outlined.Description
    FileType.ARCHIVE -> Icons.Outlined.FolderZip
    FileType.APK -> Icons.Outlined.Android
    FileType.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/** Color de acento por tipo: discreto, solo para distinguir de un vistazo. */
fun FileType.tint(colors: ColorScheme): Color = when (this) {
    FileType.FOLDER -> colors.primary
    FileType.IMAGE, FileType.VIDEO -> colors.tertiary
    FileType.AUDIO -> colors.secondary
    else -> colors.onSurfaceVariant
}

fun FileType.label(): String = when (this) {
    FileType.FOLDER -> "Carpeta"
    FileType.IMAGE -> "Imagen"
    FileType.VIDEO -> "Vídeo"
    FileType.AUDIO -> "Audio"
    FileType.DOCUMENT -> "Documento"
    FileType.ARCHIVE -> "Archivo comprimido"
    FileType.APK -> "Aplicación Android"
    FileType.OTHER -> "Archivo"
}
