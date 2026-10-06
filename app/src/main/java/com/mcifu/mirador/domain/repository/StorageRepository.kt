package com.mcifu.mirador.domain.repository

import android.content.Intent
import android.net.Uri
import com.mcifu.mirador.domain.model.UsbStorage
import kotlinx.coroutines.flow.StateFlow

/** Detección de memorias USB y gestión de los permisos del Storage Access Framework. */
interface StorageRepository {

    /** Almacenamientos conocidos; se actualiza al conectar, montar, expulsar o desconectar. */
    val storages: StateFlow<List<UsbStorage>>

    /** Fuerza un nuevo escaneo (p. ej. tras conceder o revocar un permiso). */
    fun refresh()

    /** Intent del selector del sistema, abierto directamente en la raíz del volumen si es posible. */
    fun createAccessIntent(storage: UsbStorage?): Intent

    /**
     * Persiste el permiso devuelto por el selector. Devuelve el almacenamiento resultante
     * o `null` si el URI no es un árbol válido.
     */
    suspend fun onAccessGranted(treeUri: Uri, resultFlags: Int): UsbStorage?

    /** Libera el permiso persistente de un almacenamiento. */
    suspend fun forget(storage: UsbStorage)
}
