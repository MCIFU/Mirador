package com.mcifu.mirador.ui.common

import android.content.Context
import android.text.format.Formatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    fun date(millis: Long): String =
        if (millis <= 0L) "—"
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun shortDate(millis: Long): String =
        if (millis <= 0L) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    /** 83_000 → "1:23"; 3_723_000 → "1:02:03". */
    fun duration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
