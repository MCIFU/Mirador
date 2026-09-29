package com.mcifu.usbx.domain

import com.mcifu.usbx.domain.model.BrowserSettings
import com.mcifu.usbx.domain.model.GridLayout
import com.mcifu.usbx.domain.model.ThumbnailSize
import org.junit.Assert.assertEquals
import org.junit.Test

class GridLayoutTest {

    // Galaxy M52 5G: ~412 dp de ancho en vertical, ~915 dp en horizontal.
    private val portraitWidth = 412f
    private val portraitAspect = 412f / 915f
    private val landscapeWidth = 915f
    private val landscapeAspect = 915f / 412f

    @Test
    fun `auto columns follow thumbnail size`() {
        fun cols(size: ThumbnailSize) = GridLayout.columnCount(portraitWidth, portraitAspect, BrowserSettings(thumbnailSize = size))
        assertEquals(4, cols(ThumbnailSize.SMALL))
        assertEquals(3, cols(ThumbnailSize.MEDIUM))
        assertEquals(2, cols(ThumbnailSize.LARGE))
        assertEquals(1, cols(ThumbnailSize.EXTRA_LARGE))
    }

    @Test
    fun `auto columns adapt to landscape width`() {
        val settings = BrowserSettings(thumbnailSize = ThumbnailSize.MEDIUM)
        assertEquals(7, GridLayout.columnCount(landscapeWidth, landscapeAspect, settings))
    }

    @Test
    fun `fixed columns apply in portrait and scale in landscape`() {
        val settings = BrowserSettings(columns = 3)
        assertEquals(3, GridLayout.columnCount(portraitWidth, portraitAspect, settings))
        assertEquals(7, GridLayout.columnCount(landscapeWidth, landscapeAspect, settings))
    }

    @Test
    fun `invalid values are clamped`() {
        assertEquals(6, GridLayout.columnCount(portraitWidth, portraitAspect, BrowserSettings(columns = 40)))
        assertEquals(1, GridLayout.columnCount(0f, portraitAspect, BrowserSettings()))
    }
}
