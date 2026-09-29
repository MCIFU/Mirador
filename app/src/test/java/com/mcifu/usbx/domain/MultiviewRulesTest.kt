package com.mcifu.usbx.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiviewRulesTest {

    private val w = 1000f
    private val h = 800f

    @Test
    fun `pinch around the center only scales`() {
        val t = MultiviewRules.applyGesture(ZoomTransform(), 2f, 0f, 0f, 0f, 0f, w, h)
        assertEquals(2f, t.scale)
        assertEquals(0f, t.offsetX)
        assertEquals(0f, t.offsetY)
    }

    @Test
    fun `pinch keeps the focus point under the fingers`() {
        // Punto a 200 px a la derecha del centro: tras ampliar 2x debe seguir en el mismo sitio.
        val t = MultiviewRules.applyGesture(ZoomTransform(), 2f, 0f, 0f, 200f, 0f, w, h)
        val contentX = 200f // punto de la imagen sin zoom
        assertEquals(200f, contentX * t.scale + t.offsetX, 0.01f)
    }

    @Test
    fun `scale and offsets are clamped`() {
        val big = MultiviewRules.applyGesture(ZoomTransform(), 100f, 0f, 0f, 0f, 0f, w, h)
        assertEquals(MultiviewRules.MAX_SCALE, big.scale)
        val panned = MultiviewRules.applyGesture(ZoomTransform(2f), 1f, 5_000f, -5_000f, 0f, 0f, w, h)
        assertEquals(500f, panned.offsetX) // (2 - 1) * 1000 / 2
        assertEquals(-400f, panned.offsetY)
        val unzoomed = MultiviewRules.applyGesture(ZoomTransform(), 1f, 300f, 300f, 0f, 0f, w, h)
        assertEquals(0f, unzoomed.offsetX)
    }

    @Test
    fun `double tap toggles between fit and 2_5x`() {
        val zoomed = MultiviewRules.doubleTap(ZoomTransform(), 0f, 0f, w, h)
        assertEquals(2.5f, zoomed.scale, 0.001f)
        assertEquals(ZoomTransform(), MultiviewRules.doubleTap(zoomed, 0f, 0f, w, h))
    }

    @Test
    fun `synced position keeps the offset within bounds`() {
        assertEquals(12_000L, MultiviewRules.syncedPosition(10_000, 2_000, 60_000))
        assertEquals(0L, MultiviewRules.syncedPosition(1_000, -3_000, 60_000))
        assertEquals(30_000L, MultiviewRules.syncedPosition(40_000, 0, 30_000))
    }

    @Test
    fun `resync only when drift exceeds tolerance`() {
        assertFalse(MultiviewRules.needsResync(10_000, 10_200, 0, 60_000))
        assertTrue(MultiviewRules.needsResync(10_000, 10_400, 0, 60_000))
        assertTrue(MultiviewRules.needsResync(10_000, 11_000, 2_000, 60_000))
        // B más corto y ya terminado: no se fuerza.
        assertFalse(MultiviewRules.needsResync(40_000, 30_000, 0, 30_000))
    }
}
