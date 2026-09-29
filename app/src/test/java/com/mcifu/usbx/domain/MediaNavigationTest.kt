package com.mcifu.usbx.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaNavigationTest {

    private val folder = listOf(
        item("Subcarpeta", isDirectory = true),
        item("IMG_024.jpg"),
        item("IMG_025.heic"),
        item("VID_001.mp4"),
        item("IMG_026.png"),
        item("nota.txt"),
        item("tema.mp3"),
        item("scan.tiff"),
    )

    @Test
    fun `images scope keeps only internally viewable images in order`() {
        val playlist = MediaNavigation.playlist(folder, ViewerScope.IMAGES, sdkInt = 34)
        assertEquals(listOf("IMG_024.jpg", "IMG_025.heic", "IMG_026.png"), playlist.map { it.name })
    }

    @Test
    fun `heic is skipped on old android`() {
        val playlist = MediaNavigation.playlist(folder, ViewerScope.IMAGES, sdkInt = 27)
        assertEquals(listOf("IMG_024.jpg", "IMG_026.png"), playlist.map { it.name })
    }

    @Test
    fun `other scopes`() {
        assertEquals(listOf("VID_001.mp4"), MediaNavigation.playlist(folder, ViewerScope.VIDEOS, 34).map { it.name })
        assertEquals(4, MediaNavigation.playlist(folder, ViewerScope.VISUAL_MEDIA, 34).size)
        assertEquals(5, MediaNavigation.playlist(folder, ViewerScope.ALL_MEDIA, 34).size)
    }

    @Test
    fun `start index finds the opened item or falls back to the first`() {
        val playlist = MediaNavigation.playlist(folder, ViewerScope.IMAGES, 34)
        assertEquals(1, MediaNavigation.startIndex(playlist, "1A2B-3C4D:IMG_025.heic"))
        assertEquals(0, MediaNavigation.startIndex(playlist, "1A2B-3C4D:borrada.jpg"))
    }
}
