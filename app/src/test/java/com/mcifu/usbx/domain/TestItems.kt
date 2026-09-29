package com.mcifu.usbx.domain

import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileTypeRules

internal fun item(
    name: String,
    isDirectory: Boolean = false,
    size: Long = 0,
    lastModified: Long = 0,
): FileItem {
    val mime = FileTypeRules.effectiveMime(name, null, isDirectory)
    return FileItem(
        documentId = "1A2B-3C4D:$name",
        uri = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A/document/$name",
        name = name,
        mimeType = mime,
        type = FileTypeRules.classify(name, mime, isDirectory),
        size = size,
        lastModified = lastModified,
    )
}
