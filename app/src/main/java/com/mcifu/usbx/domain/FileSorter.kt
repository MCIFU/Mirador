package com.mcifu.usbx.domain

import com.mcifu.usbx.domain.model.FileItem
import com.mcifu.usbx.domain.model.SortField
import com.mcifu.usbx.domain.model.SortOrder

/** Ordenación de carpetas. Pura y sin Android para poder probarla con tests unitarios. */
object FileSorter {

    fun sort(items: List<FileItem>, order: SortOrder, showHidden: Boolean): List<FileItem> {
        val visible = if (showHidden) items else items.filterNot { it.isHidden }
        return visible.sortedWith(comparator(order))
    }

    fun comparator(order: SortOrder): Comparator<FileItem> {
        val byName = Comparator<FileItem> { a, b -> NaturalOrder.compare(a.name, b.name) }
        val primary: Comparator<FileItem> = when (order.field) {
            SortField.NAME -> byName
            SortField.DATE -> compareBy<FileItem> { it.lastModified }.then(byName)
            SortField.SIZE -> compareBy<FileItem> { it.size }.then(byName)
            SortField.TYPE -> compareBy<FileItem> { it.extension }.then(byName)
        }
        val directed = if (order.ascending) primary else primary.reversed()
        return if (order.foldersFirst) {
            compareBy<FileItem> { if (it.isDirectory) 0 else 1 }.then(directed)
        } else {
            directed
        }
    }
}

/**
 * Orden "natural": IMG_2 va antes que IMG_10, sin distinguir mayúsculas.
 * Los bloques de dígitos se comparan por valor numérico.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
                // "01" y "1": el que tiene menos ceros a la izquierda va primero.
                val zeros = (i - startA) - (j - startB)
                if (zeros != 0) return zeros
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        val remaining = (a.length - i) - (b.length - j)
        return if (remaining != 0) remaining else a.compareTo(b)
    }
}
