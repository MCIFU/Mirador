package com.mcifu.usbx.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameRulesTest {

    @Test
    fun `valid names`() {
        assertNull(FileNameRules.validate("Vacaciones 2024"))
        assertNull(FileNameRules.validate("IMG_001.jpg"))
        assertNull(FileNameRules.validate("ñandú (copia).png"))
    }

    @Test
    fun `invalid names`() {
        assertEquals(NameError.EMPTY, FileNameRules.validate("  "))
        assertEquals(NameError.RESERVED, FileNameRules.validate(".."))
        assertEquals(NameError.INVALID_CHARS, FileNameRules.validate("a/b"))
        assertEquals(NameError.INVALID_CHARS, FileNameRules.validate("¿qué?"))
        assertEquals(NameError.INVALID_CHARS, FileNameRules.validate("a:b"))
        assertEquals(NameError.TRAILING_DOT_OR_SPACE, FileNameRules.validate("nombre."))
        assertEquals(NameError.TRAILING_DOT_OR_SPACE, FileNameRules.validate("nombre "))
        assertEquals(NameError.TOO_LONG, FileNameRules.validate("a".repeat(256)))
    }

    @Test
    fun `existing names are compared ignoring case like FAT`() {
        val existing = listOf("Foto.jpg", "Videos")
        assertEquals(NameError.ALREADY_EXISTS, FileNameRules.validate("foto.JPG", existing))
        assertEquals(NameError.ALREADY_EXISTS, FileNameRules.validate("videos", existing))
        // Renombrar solo cambiando mayúsculas del propio archivo está permitido.
        assertNull(FileNameRules.validate("FOTO.jpg", existing, currentName = "Foto.jpg"))
    }

    @Test
    fun `rename selection excludes the extension`() {
        assertEquals(7, FileNameRules.baseNameEnd("IMG_001.jpg", isDirectory = false))
        assertEquals(5, FileNameRules.baseNameEnd("a.tar.gz", isDirectory = false))
        assertEquals(5, FileNameRules.baseNameEnd("Fotos.2024", isDirectory = false))
        assertEquals(10, FileNameRules.baseNameEnd("Fotos.2024", isDirectory = true))
        assertEquals(8, FileNameRules.baseNameEnd(".nomedia", isDirectory = false))
    }

    @Test
    fun `self or descendant detection`() {
        assertTrue(FileOperationRules.isSelfOrDescendant("X:DCIM", "X:DCIM"))
        assertTrue(FileOperationRules.isSelfOrDescendant("X:DCIM", "X:DCIM/Camera"))
        assertFalse(FileOperationRules.isSelfOrDescendant("X:DCIM", "X:DCIM2"))
        assertFalse(FileOperationRules.isSelfOrDescendant("X:DCIM/Camera", "X:DCIM"))
    }

    @Test
    fun `progress fraction and space`() {
        assertEquals(0.5f, FileOperationRules.fraction(50, 100, 0, 4))
        assertEquals(0.25f, FileOperationRules.fraction(0, 0, 1, 4))
        assertEquals(0f, FileOperationRules.fraction(0, 0, 0, 0))
        assertTrue(FileOperationRules.fits(100, null))
        assertTrue(FileOperationRules.fits(100, 100))
        assertFalse(FileOperationRules.fits(101, 100))
    }
}
