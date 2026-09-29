package com.mcifu.usbx.domain.model

enum class ViewMode { LIST, GRID }

/** Tamaño de miniatura: ancho mínimo de celda en dp cuando las columnas son automáticas. */
enum class ThumbnailSize(val minCellDp: Int) {
    SMALL(84),
    MEDIUM(116),
    LARGE(164),
    EXTRA_LARGE(232),
}

data class BrowserSettings(
    val viewMode: ViewMode = ViewMode.GRID,
    val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    /** [GridLayout.AUTO] = según el tamaño de miniatura; 2..6 = columnas fijas en vertical. */
    val columns: Int = GridLayout.AUTO,
    val sortOrder: SortOrder = SortOrder(),
    val showHidden: Boolean = false,
)

/**
 * Cálculo de columnas de la cuadrícula.
 *
 * "Tamaño de miniatura" y "número de columnas" son dos formas de controlar lo mismo
 * (el ancho de celda). Por eso: en AUTO manda el tamaño; con columnas fijas manda el número,
 * que se interpreta para vertical y se escala en horizontal para que las celdas no se vuelvan
 * gigantes al girar el móvil.
 */
object GridLayout {
    const val AUTO = 0
    val COLUMN_OPTIONS = listOf(2, 3, 4, 5, 6)

    /**
     * @param widthDp ancho disponible para la cuadrícula.
     * @param screenAspect ancho/alto de la pantalla (> 1 en horizontal).
     */
    fun columnCount(widthDp: Float, screenAspect: Float, settings: BrowserSettings): Int {
        if (widthDp <= 0f) return 1
        if (settings.columns == AUTO) {
            return (widthDp / settings.thumbnailSize.minCellDp).toInt().coerceAtLeast(1)
        }
        val portraitColumns = settings.columns.coerceIn(COLUMN_OPTIONS.first(), COLUMN_OPTIONS.last())
        if (screenAspect <= 1f) return portraitColumns
        return Math.round(portraitColumns * screenAspect).coerceAtLeast(portraitColumns)
    }
}
