package com.mcifu.mirador.data.storage

import android.net.Uri
import androidx.core.net.toUri
import com.mcifu.mirador.domain.model.FileItem

/** URI de Android del documento. `Uri.parse` es perezoso y barato. */
val FileItem.contentUri: Uri get() = uri.toUri()
