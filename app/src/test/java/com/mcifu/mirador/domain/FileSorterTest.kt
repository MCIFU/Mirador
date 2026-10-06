package com.mcifu.mirador.domain

import com.mcifu.mirador.domain.model.SortField
import com.mcifu.mirador.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSorterTest {

    @Test
    fun `natural order sorts numbers by value and ignores case`() {
        val names = listOf("IMG_10.jpg", "img_2.jpg", "IMG_1.jpg", "IMG_100.jpg", "a.jpg")
        assertEquals(
            listOf("a.jpg", "IMG_1.jpg", "img_2.jpg", "IMG_10.jpg", "IMG_100.jpg"),
            names.sortedWith(NaturalOrder),
        )
    }

    @Test
    fun `natural order is consistent with leading zeros and long numbers`() {
        assertTrue(NaturalOrder.compare("file1", "file01") < 0)
        assertTrue(NaturalOrder.compare("file01", "file2") < 0)
        assertTrue(NaturalOrder.compare("20240101_123456789012", "20240101_123456789013") < 0)
        assertEquals(0, NaturalOrder.compare("same", "same"))
    }

    @Test
    fun `folders first then name`() {
        val items = listOf(item("b.jpg"), item("Zeta", isDirectory = true), item("a.mp4"), item("Alfa", isDirectory = true))
        val sorted = FileSorter.sort(items, SortOrder(), showHidden = false).map { it.name }
        assertEquals(listOf("Alfa", "Zeta", "a.mp4", "b.jpg"), sorted)
    }

    @Test
    fun `sort by date descending keeps folders first`() {
        val items = listOf(
            item("old.jpg", lastModified = 1),
            item("new.jpg", lastModified = 3),
            item("Carpeta", isDirectory = true, lastModified = 0),
            item("mid.jpg", lastModified = 2),
        )
        val sorted = FileSorter.sort(items, SortOrder(SortField.DATE, ascending = false), showHidden = false)
        assertEquals(listOf("Carpeta", "new.jpg", "mid.jpg", "old.jpg"), sorted.map { it.name })
    }

    @Test
    fun `sort by size without folders first`() {
        val items = listOf(item("big.mp4", size = 300), item("Dir", isDirectory = true, size = 0), item("small.jpg", size = 10))
        val sorted = FileSorter.sort(items, SortOrder(SortField.SIZE, ascending = true, foldersFirst = false), showHidden = false)
        assertEquals(listOf("Dir", "small.jpg", "big.mp4"), sorted.map { it.name })
    }

    @Test
    fun `hidden files are filtered unless requested`() {
        val items = listOf(item(".nomedia"), item("DCIM", isDirectory = true), item("LOST.DIR", isDirectory = true))
        assertEquals(listOf("DCIM"), FileSorter.sort(items, SortOrder(), showHidden = false).map { it.name })
        assertEquals(3, FileSorter.sort(items, SortOrder(), showHidden = true).size)
    }

    @Test
    fun `sorting thousands of items is fast`() {
        val items = (1..20_000).map { item("IMG_${(it * 7919) % 20_000}.jpg", lastModified = it.toLong()) }
        val start = System.nanoTime()
        val sorted = FileSorter.sort(items, SortOrder(), showHidden = false)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(20_000, sorted.size)
        assertEquals("IMG_0.jpg", sorted.first().name)
        assertTrue("Ordenar 20.000 elementos tardó $elapsedMs ms", elapsedMs < 2_000)
    }
}
