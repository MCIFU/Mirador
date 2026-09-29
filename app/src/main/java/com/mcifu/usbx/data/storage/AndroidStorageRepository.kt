package com.mcifu.usbx.data.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mcifu.usbx.domain.model.MountState
import com.mcifu.usbx.domain.model.StorageAccess
import com.mcifu.usbx.domain.model.StorageKind
import com.mcifu.usbx.domain.model.UsbStorage
import com.mcifu.usbx.domain.repository.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detecta memorias USB con las APIs públicas de Android:
 *
 * - [StorageManager.getStorageVolumes] para enumerar volúmenes extraíbles y su estado.
 * - Broadcasts `ACTION_MEDIA_*` y, en Android 11+, `StorageVolumeCallback` para enterarse al
 *   instante de montajes, expulsiones y desconexiones.
 * - Permisos persistentes del Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) para leer.
 *
 * El volumen se identifica por su UUID, que coincide con el prefijo del documentId del
 * `ExternalStorageProvider` ("1A2B-3C4D:DCIM/Camera"). Así el permiso concedido se reconoce
 * cuando la misma memoria se vuelve a conectar.
 */
class AndroidStorageRepository(
    private val context: Context,
    appScope: CoroutineScope,
) : StorageRepository {

    private val storageManager = context.getSystemService(StorageManager::class.java)
    private val labels = context.getSharedPreferences("storage_labels", Context.MODE_PRIVATE)
    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _storages = MutableStateFlow(scan())
    override val storages: StateFlow<List<UsbStorage>> = _storages.asStateFlow()

    init {
        // Escucha durante toda la vida del proceso: el coste es mínimo y así el estado es
        // correcto aunque la desconexión ocurra con la app en segundo plano.
        appScope.launch(Dispatchers.IO) {
            merge(systemStorageEvents(), manualRefresh)
                .conflate()
                .collect { _storages.value = scan() }
        }
    }

    override fun refresh() {
        manualRefresh.tryEmit(Unit)
    }

    override fun createAccessIntent(storage: UsbStorage?): Intent {
        val volume = storage?.let { findVolume(it.id) }
        if (volume != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Abre el selector del sistema directamente en la raíz de la memoria USB.
            return volume.createOpenDocumentTreeIntent()
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (storage != null && volume != null) {
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "${storage.id}:"),
                )
            }
        }
    }

    override suspend fun onAccessGranted(treeUri: Uri, resultFlags: Int): UsbStorage? =
        withContext(Dispatchers.IO) {
            if (!DocumentsContract.isTreeUri(treeUri)) return@withContext null
            val flags = resultFlags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val takeFlags = if (flags == 0) Intent.FLAG_GRANT_READ_URI_PERMISSION else flags
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: SecurityException) {
                return@withContext null
            }

            val newId = storageIdFor(treeUri)
            // Un único permiso por volumen: se liberan los anteriores (p. ej. una subcarpeta).
            context.contentResolver.persistedUriPermissions
                .filter { it.uri != treeUri && storageIdFor(it.uri) == newId }
                .forEach { releaseQuietly(it.uri) }

            val volume = findVolume(newId)
            labels.edit { putString(newId, volume?.getDescription(context) ?: folderLabel(treeUri)) }

            // Actualización síncrona: quien navegue a continuación ya ve el permiso concedido.
            val updated = scan()
            _storages.value = updated
            updated.firstOrNull { it.id == newId }
        }

    override suspend fun forget(storage: UsbStorage) = withContext(Dispatchers.IO) {
        storage.access?.let { releaseQuietly(it.treeUri) }
        labels.edit { remove(storage.id) }
        _storages.value = scan()
    }

    // --- Escaneo -------------------------------------------------------------------------

    private fun scan(): List<UsbStorage> {
        val grants = try {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
                .map { it.uri }
        } catch (_: Exception) {
            emptyList()
        }

        val volumes = try {
            storageManager.storageVolumes.filter { it.isRemovable && !it.isPrimary && it.uuid != null }
        } catch (_: Exception) {
            emptyList()
        }

        val fromVolumes = volumes.map { volume ->
            val uuid = volume.uuid!!
            val space = spaceOf(volume)
            UsbStorage(
                id = uuid,
                label = volume.getDescription(context),
                kind = guessKind(volume),
                mountState = mountStateOf(volume.state),
                access = bestGrantFor(uuid, grants),
                totalBytes = space?.first,
                freeBytes = space?.second,
            )
        }

        // Permisos concedidos para almacenamientos que no están conectados ahora mismo
        // (memoria USB desconectada) o para carpetas elegidas manualmente.
        val knownIds = fromVolumes.map { it.id }.toSet()
        val others = grants
            .groupBy { storageIdFor(it) }
            .filterKeys { it !in knownIds }
            .map { (id, uris) ->
                val uri = uris.minBy { DocumentsContract.getTreeDocumentId(it).length }
                val isRemovableVolume = uri.authority == EXTERNAL_STORAGE_AUTHORITY && id != "primary" &&
                    id == volumeIdOf(uri)
                UsbStorage(
                    id = id,
                    label = labels.getString(id, null) ?: folderLabel(uri),
                    kind = if (isRemovableVolume) StorageKind.USB else StorageKind.FOLDER,
                    // Un volumen extraíble que no aparece en StorageManager está desconectado.
                    // Una carpeta de otro proveedor se considera disponible; los errores de lectura
                    // se gestionan al abrirla.
                    mountState = if (isRemovableVolume) MountState.UNAVAILABLE else MountState.MOUNTED,
                    access = StorageAccess(uri, DocumentsContract.getTreeDocumentId(uri)),
                )
            }

        return fromVolumes + others
    }

    private fun bestGrantFor(uuid: String, grants: List<Uri>): StorageAccess? =
        grants
            .filter { it.authority == EXTERNAL_STORAGE_AUTHORITY && volumeIdOf(it) == uuid }
            // Preferimos el permiso sobre la raíz ("UUID:") frente a una subcarpeta.
            .minByOrNull { DocumentsContract.getTreeDocumentId(it).length }
            ?.let { StorageAccess(it, DocumentsContract.getTreeDocumentId(it)) }

    private fun findVolume(uuid: String): StorageVolume? =
        try {
            storageManager.storageVolumes.firstOrNull { it.uuid == uuid }
        } catch (_: Exception) {
            null
        }

    private fun guessKind(volume: StorageVolume): StorageKind {
        // Android no expone públicamente si un volumen es USB o SD; la descripción del sistema
        // ("Memoria USB SanDisk", "Tarjeta SD") es la señal más fiable disponible.
        val description = volume.getDescription(context).lowercase()
        return when {
            "usb" in description -> StorageKind.USB
            "sd" in description -> StorageKind.SD_CARD
            else -> StorageKind.USB
        }
    }

    @Suppress("UsableSpace") // En un volumen extraíble no hay caché que liberar: usableSpace es lo correcto.
    private fun spaceOf(volume: StorageVolume): Pair<Long, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val dir = volume.directory ?: return null
            val total = dir.totalSpace
            if (total <= 0L) null else total to dir.usableSpace
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseQuietly(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            try {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
        }
    }

    // --- Eventos del sistema -------------------------------------------------------------

    private fun systemStorageEvents(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_CHECKING)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_UNMOUNTABLE)
            addAction(Intent.ACTION_MEDIA_NOFS)
            addDataScheme("file")
        }
        // Son broadcasts protegidos del sistema: nadie más puede enviarlos.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val volumeCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    trySend(Unit)
                }
            }.also { storageManager.registerStorageVolumeCallback(context.mainExecutor, it) }
        } else {
            null
        }

        awaitClose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && volumeCallback != null) {
                storageManager.unregisterStorageVolumeCallback(volumeCallback)
            }
        }
    }

    companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        private fun mountStateOf(state: String?): MountState = when (state) {
            Environment.MEDIA_MOUNTED -> MountState.MOUNTED
            Environment.MEDIA_MOUNTED_READ_ONLY -> MountState.READ_ONLY
            Environment.MEDIA_CHECKING -> MountState.CHECKING
            else -> MountState.UNAVAILABLE
        }

        /** "1A2B-3C4D" para `.../tree/1A2B-3C4D%3ADCIM`. */
        private fun volumeIdOf(treeUri: Uri): String? =
            DocumentsContract.getTreeDocumentId(treeUri).substringBefore(':', missingDelimiterValue = "")
                .ifEmpty { null }

        /** Id estable de almacenamiento: UUID del volumen o, si no aplica, el URI del árbol. */
        fun storageIdFor(treeUri: Uri): String {
            if (treeUri.authority == EXTERNAL_STORAGE_AUTHORITY) {
                val volumeId = volumeIdOf(treeUri)
                if (volumeId != null && volumeId != "primary" && volumeId != "home") return volumeId
            }
            return treeUri.toString()
        }

        private fun folderLabel(treeUri: Uri): String {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val path = docId.substringAfter(':', docId).trimEnd('/')
            return path.substringAfterLast('/').ifEmpty { "Carpeta" }
        }
    }
}
