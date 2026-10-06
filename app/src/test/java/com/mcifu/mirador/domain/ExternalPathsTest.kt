package com.mcifu.mirador.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPathsTest {

    @Test
    fun `usb path maps to document and parent`() {
        val doc = ExternalPaths.volumeDocumentFor("/storage/1A2B-3C4D/DCIM/Camera/IMG_001.jpg")!!
        assertEquals("1A2B-3C4D", doc.volumeId)
        assertEquals("1A2B-3C4D:DCIM/Camera/IMG_001.jpg", doc.documentId)
        assertEquals("1A2B-3C4D:DCIM/Camera", doc.parentDocumentId)
    }

    @Test
    fun `file at the root has the root as parent`() {
        val doc = ExternalPaths.volumeDocumentFor("/storage/1A2B-3C4D/video.mp4")!!
        assertEquals("1A2B-3C4D:", doc.parentDocumentId)
    }

    @Test
    fun `internal storage and invalid paths are ignored`() {
        assertNull(ExternalPaths.volumeDocumentFor("/storage/emulated/0/DCIM/a.jpg"))
        assertNull(ExternalPaths.volumeDocumentFor("/storage/self/primary/a.jpg"))
        assertNull(ExternalPaths.volumeDocumentFor("/data/user/0/app/a.jpg"))
        assertNull(ExternalPaths.volumeDocumentFor("/storage/1A2B-3C4D"))
        assertNull(ExternalPaths.volumeDocumentFor(null))
    }

    @Test
    fun `parent of document ids`() {
        assertEquals("1A2B-3C4D:DCIM", ExternalPaths.parentOf("1A2B-3C4D:DCIM/a.jpg"))
        assertEquals("1A2B-3C4D:", ExternalPaths.parentOf("1A2B-3C4D:a.jpg"))
        assertNull(ExternalPaths.parentOf("1A2B-3C4D:"))
        assertNull(ExternalPaths.parentOf("sin-volumen"))
    }

    @Test
    fun `tree containment`() {
        assertTrue(ExternalPaths.isInsideTree("1A2B-3C4D:DCIM/a.jpg", "1A2B-3C4D:"))
        assertTrue(ExternalPaths.isInsideTree("1A2B-3C4D:DCIM/a.jpg", "1A2B-3C4D:DCIM"))
        assertFalse(ExternalPaths.isInsideTree("1A2B-3C4D:DCIMX/a.jpg", "1A2B-3C4D:DCIM"))
        assertFalse(ExternalPaths.isInsideTree("9999-0000:DCIM/a.jpg", "1A2B-3C4D:"))
    }
}
