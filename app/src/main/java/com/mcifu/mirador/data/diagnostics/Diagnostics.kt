package com.mcifu.mirador.data.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico local (nunca sale del móvil salvo que el usuario lo comparta).
 *
 * Guarda los últimos eventos relevantes (qué se abre dentro o fuera y por qué, errores de lectura,
 * de miniaturas y de reproducción) y el último cierre inesperado. Sirve para diagnosticar fallos
 * en dispositivos reales sin cable ni herramientas de desarrollo.
 */
object Diagnostics {
    private const val TAG = "Mirador"
    private const val MAX_EVENTS = 300
    private val events = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private var crashFile: File? = null
    private var logFile: File? = null

    fun install(context: Context) {
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        crashFile = File(dir, "last_crash.txt")
        logFile = File(dir, "events.txt")
        // Recupera los eventos de la sesión anterior (útil si la app se cerró).
        logFile?.takeIf { it.exists() }?.readLines()?.takeLast(MAX_EVENTS)?.forEach { synchronized(events) { events.addLast(it) } }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashFile?.writeText("${Date()}\n${stackTrace(error)}")
                log("CIERRE", "Excepción no controlada en ${thread.name}: ${error.javaClass.name}: ${error.message}")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun log(category: String, message: String, error: Throwable? = null) {
        val detail = error?.let { " | ${it.javaClass.simpleName}: ${it.message}${it.cause?.let { c -> " ← ${c.javaClass.simpleName}: ${c.message}" }.orEmpty()}" }.orEmpty()
        val line = "${timeFormat.format(Date())} [$category] $message$detail"
        if (error != null) Log.w(TAG, line, error) else Log.i(TAG, line)
        synchronized(events) {
            events.addLast(line)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
        try {
            logFile?.appendText(line + "\n")
            logFile?.let { f -> if (f.length() > 256_000) f.writeText(synchronized(events) { events.joinToString("\n") } + "\n") }
        } catch (_: Exception) {
        }
    }

    fun lastCrash(): String? = crashFile?.takeIf { it.exists() }?.readText()

    fun clearCrash() {
        crashFile?.delete()
    }

    fun clear() {
        synchronized(events) { events.clear() }
        logFile?.delete()
        clearCrash()
    }

    /** Informe completo en texto plano para copiar o compartir. */
    fun report(context: Context, storageSummary: String): String = buildString {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        appendLine("=== Informe de diagnóstico Mirador ===")
        appendLine("App: ${context.packageName} $version")
        appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("--- Almacenamiento ---")
        appendLine(storageSummary)
        appendLine()
        appendLine("--- Últimos eventos ---")
        synchronized(events) { events.forEach { appendLine(it) } }
        lastCrash()?.let {
            appendLine()
            appendLine("--- Último cierre inesperado ---")
            appendLine(it.take(8_000))
        }
    }

    private fun stackTrace(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
