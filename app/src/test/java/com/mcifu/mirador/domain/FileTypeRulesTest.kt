package com.mcifu.mirador.domain

import com.mcifu.mirador.domain.model.FileType
import com.mcifu.mirador.domain.model.FileTypeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypeRulesTest {

    @Test
    fun `classifies common formats by extension`() {
        val cases = mapOf(
            "IMG_001.JPG" to FileType.IMAGE, "foto.heic" to FileType.IMAGE, "a.webp" to FileType.IMAGE,
            "video.mp4" to FileType.VIDEO, "peli.MKV" to FileType.VIDEO, "clip.mov" to FileType.VIDEO,
            "viejo.avi" to FileType.VIDEO, "web.webm" to FileType.VIDEO,
            "tema.mp3" to FileType.AUDIO, "a.flac" to FileType.AUDIO, "b.m4a" to FileType.AUDIO,
            "c.ogg" to FileType.AUDIO, "d.wav" to FileType.AUDIO, "e.aac" to FileType.AUDIO,
            "doc.pdf" to FileType.DOCUMENT, "hoja.xlsx" to FileType.DOCUMENT, "notas.txt" to FileType.DOCUMENT,
            "backup.zip" to FileType.ARCHIVE, "app.apk" to FileType.APK, "raro.xyz" to FileType.OTHER,
        )
        cases.forEach { (name, expected) ->
            assertEquals(name, expected, FileTypeRules.classify(name, null, isDirectory = false))
        }
    }

    @Test
    fun `directories are folders regardless of name`() {
        assertEquals(FileType.FOLDER, FileTypeRules.classify("fotos.jpg", null, isDirectory = true))
        assertEquals(FileType.FOLDER, FileTypeRules.classify("x", FileTypeRules.MIME_DIRECTORY, isDirectory = false))
    }

    @Test
    fun `falls back to mime type when extension is unknown`() {
        assertEquals(FileType.IMAGE, FileTypeRules.classify("sin_extension", "image/png", false))
        assertEquals(FileType.VIDEO, FileTypeRules.classify("sin_extension", "video/mp4", false))
    }

    @Test
    fun `effective mime prefers specific provider mime and falls back to extension`() {
        assertEquals("video/mp4", FileTypeRules.effectiveMime("a.mkv", "video/mp4", false))
        assertEquals("video/x-matroska", FileTypeRules.effectiveMime("a.mkv", "application/octet-stream", false))
        assertEquals("image/heic", FileTypeRules.effectiveMime("a.HEIC", null, false))
        assertEquals(FileTypeRules.MIME_UNKNOWN, FileTypeRules.effectiveMime("a.qqq", null, false))
    }

    @Test
    fun `extension handling`() {
        assertEquals("jpg", FileTypeRules.extensionOf("IMG.JPG"))
        assertEquals("gz", FileTypeRules.extensionOf("a.tar.gz"))
        assertEquals("", FileTypeRules.extensionOf(".nomedia"))
        assertEquals("", FileTypeRules.extensionOf("README"))
        assertEquals("", FileTypeRules.extensionOf("raro."))
    }

    @Test
    fun `hidden and system names`() {
        assertTrue(FileTypeRules.isHiddenName(".thumbnails"))
        assertTrue(FileTypeRules.isHiddenName("System Volume Information"))
        assertTrue(FileTypeRules.isHiddenName("\$RECYCLE.BIN"))
        assertFalse(FileTypeRules.isHiddenName("DCIM"))
    }

    @Test
    fun `internal viewer support depends on android version`() {
        assertTrue(FileTypeRules.isInternallyViewableImage("a.jpg", "image/jpeg", 26))
        assertFalse(FileTypeRules.isInternallyViewableImage("a.heic", "image/heic", 27))
        assertTrue(FileTypeRules.isInternallyViewableImage("a.heic", "image/heic", 28))
        assertFalse(FileTypeRules.isInternallyViewableImage("a.avif", "image/avif", 30))
        assertTrue(FileTypeRules.isInternallyViewableImage("a.avif", "image/avif", 31))
        assertFalse(FileTypeRules.isInternallyViewableImage("a.tiff", "image/tiff", 34))
        assertFalse(FileTypeRules.isInternallyViewableImage("a.svg", "image/svg+xml", 34))
    }
}
