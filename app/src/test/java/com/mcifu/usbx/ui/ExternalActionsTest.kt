package com.mcifu.usbx.ui

import com.mcifu.usbx.ui.common.ExternalActions
import com.mcifu.usbx.ui.common.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalActionsTest {

    @Test
    fun `common mime for sharing several files`() {
        assertEquals("image/jpeg", ExternalActions.commonMime(listOf("image/jpeg", "image/jpeg")))
        assertEquals("image/*", ExternalActions.commonMime(listOf("image/jpeg", "image/png")))
        assertEquals("*/*", ExternalActions.commonMime(listOf("image/jpeg", "video/mp4")))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("0:00", Formatters.duration(0))
        assertEquals("1:23", Formatters.duration(83_000))
        assertEquals("1:02:03", Formatters.duration(3_723_000))
    }
}
