package com.mcifu.usbx.data.storage

import android.net.Uri
import androidx.core.net.toUri
import com.mcifu.usbx.domain.model.FileItem

/** URI de Android del documento. `Uri.parse` es perezoso y barato. */
val FileItem.contentUri: Uri get() = uri.toUri()
