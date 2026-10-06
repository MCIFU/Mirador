package com.mcifu.mirador.domain.repository

import com.mcifu.mirador.domain.model.FileDetails
import com.mcifu.mirador.domain.model.FileItem

interface FileDetailsRepository {
    /**
     * Lee dimensiones, duración y metadatos. Nunca lanza por un archivo ilegible: devuelve
     * lo que haya podido obtener.
     */
    suspend fun loadDetails(item: FileItem, storageLabel: String, parentPath: List<String>): FileDetails
}
