package com.mcifu.mirador.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Colores de Material You (fondo de pantalla) en Android 12+; si no, la paleta propia de Mirador. */
    val dynamicColor: Boolean = false,
    /** Pedir huella, PIN o patrón del móvil antes de mostrar las carpetas guardadas. */
    val lockSavedFolders: Boolean = true,
)
