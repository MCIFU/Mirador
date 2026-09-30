package com.mcifu.usbx.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.mcifu.usbx.data.diagnostics.Diagnostics
import com.mcifu.usbx.data.storage.contentUri
import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.FileTypeRules

/**
 * Abrir y compartir con otras apps. Se concede a la app destino permiso temporal de lectura
 * sobre el URI del documento; USBX nunca copia el archivo.
 */
object ExternalActions {

    sealed interface OpenResult {
        data object Opened : OpenResult
        /** Ninguna app acepta este tipo concreto; [canOpenGeneric] indica si hay apps genéricas. */
        data class NoApp(val canOpenGeneric: Boolean) : OpenResult
    }

    fun open(context: Context, item: FileItem): OpenResult {
        val intent = viewIntent(item, item.mimeType)
        return try {
            context.startActivity(intent)
            Diagnostics.log("EXTERNO", "Abierto con otra app: ${item.name} (${item.mimeType})")
            OpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            OpenResult.NoApp(canOpenGeneric = hasHandler(context, viewIntent(item, "*/*")))
        } catch (_: SecurityException) {
            OpenResult.NoApp(canOpenGeneric = false)
        }
    }

    /** Muestra siempre el selector de apps. Con [anyType] se ofrecen apps que aceptan cualquier archivo. */
    fun openWith(context: Context, item: FileItem, anyType: Boolean = false): Boolean {
        val mime = if (anyType || item.mimeType == FileTypeRules.MIME_UNKNOWN) "*/*" else item.mimeType
        return startSafely(context, Intent.createChooser(viewIntent(item, mime), "Abrir con"))
    }

    fun share(context: Context, items: List<FileItem>): Boolean {
        val files = items.filterNot { it.isDirectory }
        if (files.isEmpty()) return false
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = files.first().mimeType
                putExtra(Intent.EXTRA_STREAM, files.first().contentUri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMime(files.map { it.mimeType })
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { it.contentUri }))
            }
        }.apply {
            clipData = ClipData.newRawUri(null, files.first().contentUri).also { clip ->
                files.drop(1).forEach { clip.addItem(ClipData.Item(it.contentUri)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startSafely(context, Intent.createChooser(intent, "Compartir"))
    }

    private fun viewIntent(item: FileItem, mime: String) = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.contentUri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun hasHandler(context: Context, intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

    private fun startSafely(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    /** "image/jpeg" + "image/png" → "image/\*"; tipos distintos → "\*\/\*". */
    internal fun commonMime(mimes: List<String>): String {
        val distinct = mimes.distinct()
        if (distinct.size == 1) return distinct.first()
        val families = distinct.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.first()}/*" else "*/*"
    }
}
