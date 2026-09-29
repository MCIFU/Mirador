package com.mcifu.usbx.data

import com.mcifu.usbx.data.thumbnail.ThumbnailFetcher
import com.mcifu.usbx.data.thumbnail.ThumbnailGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailMathTest {

    @Test
    fun `sample size keeps the short side above the target`() {
        // Foto de 12 MP (4000x3000) para una celda de 320 px: 3000/8 = 375 ≥ 320, 3000/16 < 320.
        assertEquals(8, ThumbnailGenerator.sampleSizeFor(4000, 3000, 320))
        assertEquals(1, ThumbnailGenerator.sampleSizeFor(400, 300, 320))
        assertEquals(1, ThumbnailGenerator.sampleSizeFor(0, 0, 320))
        assertEquals(16, ThumbnailGenerator.sampleSizeFor(8160, 6120, 256))
    }

    @Test
    fun `requested sizes are grouped in buckets`() {
        assertEquals(160, ThumbnailFetcher.bucketFor(126))
        assertEquals(384, ThumbnailFetcher.bucketFor(400))
        assertEquals(512, ThumbnailFetcher.bucketFor(500))
        assertEquals(768, ThumbnailFetcher.bucketFor(5000))
    }
}
