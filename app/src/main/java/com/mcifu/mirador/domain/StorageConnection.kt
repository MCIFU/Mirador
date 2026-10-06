package com.mcifu.mirador.domain

import com.mcifu.mirador.domain.model.UsbStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * Transiciones de conexión de un almacenamiento concreto.
 * Emite `true` al (re)conectarse y `false` al desconectarse; ignora el estado inicial.
 */
fun Flow<List<UsbStorage>>.connectionChanges(storageId: String): Flow<Boolean> =
    map { storages -> storages.firstOrNull { it.id == storageId }?.canBrowse == true }
        .distinctUntilChanged()
        .drop(1)
